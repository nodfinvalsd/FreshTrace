package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.redis.RedisLuaExecutor;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.trade.enums.ReconcileStatus;
import com.freshtrace.trade.service.InventoryReconciliationService;
import com.freshtrace.trade.support.ReconciliationResult;
import com.freshtrace.trade.support.StockDeductionSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 库存对账。MySQL t_product.stock 为最终事实，修复方向永远 MySQL → Redis。
 * <p>
 * 并发安全（§9 核心难点）：
 * 普通修复（无脑 SET）存在覆盖竞态——下单流程是「Redis Lua 预扣 → MySQL 事务扣减」两阶段，
 * 若修复恰在下单的 Redis 扣减之后、MySQL 提交之前覆盖 Redis，会把这次扣减冲掉。
 * 因此修复采用：
 * 1. CAS 写入：Lua 内比对「当前 Redis 值 == 检测时读到的旧值」才允许覆盖；
 *    旧值已变（说明有并发扣减/恢复发生）则放弃本轮写入；
 * 2. 写入后二次校验：重新读 MySQL 与 Redis，相等才算 REPAIRED，否则重试（最多 3 次）。
 * 该协议下任意交错最终收敛到 MySQL == Redis，不会覆盖在途库存操作。
 * <p>
 * 不猜测异常：Redis 非数字值 / MySQL 负库存一律 REPAIR_FAILED + ERROR，绝不自动拍值。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReconciliationServiceImpl implements InventoryReconciliationService {

    private static final int PAGE_SIZE = 200;
    private static final int REPAIR_MAX_ATTEMPTS = 3;
    private static final String MISSING_SENTINEL = "__MISSING__";

    /**
     * CAS 修复：仅当当前值等于 expected 时才写入 newValue。
     * expected = "__MISSING__" 时表示 key 不存在（不存在才初始化）。
     * 返回 1 = 已写入；0 = 值已变化（存在并发操作），调用方应重读重试。
     */
    private static final String CAS_SET_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if ARGV[1] == '__MISSING__' then
                if current == false then
                    redis.call('SET', KEYS[1], ARGV[2])
                    return 1
                end
                return 0
            end
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private final ProductMapper productMapper;
    private final StockDeductionSupport stockDeductionSupport;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisLuaExecutor redisLuaExecutor;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ReconciliationResult scanAndRepair() {
        ReconciliationResult result = new ReconciliationResult();
        long pageNo = 1;
        while (true) {
            Page<Product> page = new Page<>(pageNo, PAGE_SIZE);
            productMapper.selectPage(page, new LambdaQueryWrapper<Product>().orderByAsc(Product::getId));
            List<Product> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            for (Product product : records) {
                result.incrementScanned();
                try {
                    result.recordRepair(reconcileOne(product, result));
                } catch (Exception e) {
                    log.error("Inventory reconcile unexpected error, productId={}", product.getId(), e);
                    result.recordRepair(ReconcileStatus.REPAIR_FAILED);
                }
            }
            if (records.size() < PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        log.info("Inventory reconciliation finished, {}", result);
        return result;
    }

    private ReconcileStatus reconcileOne(Product product, ReconciliationResult result) {
        Long productId = product.getId();
        String key = stockDeductionSupport.keyOf(productId);
        int mysqlStock = product.getStock() == null ? 0 : product.getStock();

        if (mysqlStock < 0) {
            // 严重异常：不确定正确库存，绝不自动修成 0，也不动 Redis
            log.error("Inventory anomaly: negative mysql stock, productId={}, mysqlStock={}", productId, mysqlStock);
            result.incrementMismatch();
            return ReconcileStatus.REPAIR_FAILED;
        }

        String redisValue;
        try {
            redisValue = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Inventory check failed, redis read error, productId={}", productId, e);
            result.incrementMismatch();
            return ReconcileStatus.REPAIR_FAILED;
        }

        if (redisValue != null && !isNumeric(redisValue)) {
            // Redis 数据异常：不确定来源，不自动覆盖，仅告警
            log.error("Inventory anomaly: non-numeric redis value, productId={}, redisValue={}", productId, redisValue);
            result.incrementMismatch();
            return ReconcileStatus.REPAIR_FAILED;
        }

        if (redisValue != null && Long.parseLong(redisValue.trim()) == mysqlStock) {
            return ReconcileStatus.CONSISTENT;
        }
        if (redisValue == null) {
            result.incrementMissing();
            log.warn("Inventory mismatch detected, productId={}, mysqlStock={}, redis=missing", productId, mysqlStock);
        } else {
            result.incrementMismatch();
            log.warn("Inventory mismatch detected, productId={}, mysqlStock={}, redisStock={}",
                    productId, mysqlStock, redisValue);
        }
        return repair(productId);
    }

    /**
     * 以 MySQL 为基准修复 Redis：CAS 写入 + 二次校验 + 限次重试。
     */
    private ReconcileStatus repair(Long productId) {
        String key = stockDeductionSupport.keyOf(productId);
        for (int attempt = 1; attempt <= REPAIR_MAX_ATTEMPTS; attempt++) {
            Integer mysqlStock = readMysqlStock(productId);
            if (mysqlStock == null) {
                log.warn("Inventory repair skipped, product deleted, productId={}", productId);
                return ReconcileStatus.REPAIR_FAILED;
            }
            if (mysqlStock < 0) {
                log.error("Inventory anomaly: negative mysql stock, productId={}, mysqlStock={}", productId, mysqlStock);
                return ReconcileStatus.REPAIR_FAILED;
            }

            String current;
            try {
                current = stringRedisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.error("Inventory repair failed, redis read error, productId={}", productId, e);
                return ReconcileStatus.REPAIR_FAILED;
            }
            if (current != null && !isNumeric(current)) {
                log.error("Inventory anomaly: non-numeric redis value, productId={}, redisValue={}", productId, current);
                return ReconcileStatus.REPAIR_FAILED;
            }
            if (current != null && Long.parseLong(current.trim()) == mysqlStock) {
                return ReconcileStatus.REPAIRED;
            }

            long casResult;
            try {
                casResult = redisLuaExecutor.executeLong(CAS_SET_SCRIPT, List.of(key),
                        current == null ? MISSING_SENTINEL : current, String.valueOf(mysqlStock));
            } catch (Exception e) {
                log.error("Inventory repair failed, redis write error, productId={}, mysqlStock={}",
                        productId, mysqlStock, e);
                return ReconcileStatus.REPAIR_FAILED;
            }

            // 二次校验：修复成功的唯一标准是 Redis == 最新 MySQL。
            // MySQL 侧使用 FOR UPDATE 锁定读：若下单扣减事务仍在途（Redis 已扣、MySQL 未提交），
            // 此读会阻塞至其提交，从而读到包含该扣减的最终值，杜绝「对账覆盖在途扣减」的误判窗口。
            Integer mysqlAfter = readMysqlStockForUpdate(productId);
            String redisAfter = stringRedisTemplate.opsForValue().get(key);
            if (mysqlAfter != null && redisAfter != null && isNumeric(redisAfter)
                    && mysqlAfter.intValue() == Long.parseLong(redisAfter.trim())) {
                log.warn("Inventory repaired, productId={}, from={}, to={}",
                        productId, current == null ? "missing" : current, mysqlAfter);
                return ReconcileStatus.REPAIRED;
            }
            if (casResult == 0) {
                log.info("Inventory repair conflict, concurrent stock change detected, productId={}, attempt={}/{}",
                        productId, attempt, REPAIR_MAX_ATTEMPTS);
            } else {
                log.warn("Inventory repair verify failed (attempt {}/{}), productId={}, mysqlAfter={}, redisAfter={}",
                        attempt, REPAIR_MAX_ATTEMPTS, productId, mysqlAfter, redisAfter);
            }
        }
        log.error("Inventory repair failed after retries, productId={}", productId);
        return ReconcileStatus.REPAIR_FAILED;
    }

    private Integer readMysqlStock(Long productId) {
        try {
            Product latest = productMapper.selectById(productId);
            return latest == null ? null : latest.getStock();
        } catch (Exception e) {
            log.error("Inventory repair failed, mysql read error, productId={}", productId, e);
            return null;
        }
    }

    /**
     * 锁定读（FOR UPDATE）：阻塞至该商品行上在途的写事务提交，读到的是确定的最新库存。
     */
    private Integer readMysqlStockForUpdate(Long productId) {
        try {
            return transactionTemplate.execute(status -> {
                Product latest = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                        .eq(Product::getId, productId)
                        .last("FOR UPDATE"));
                return latest == null ? null : latest.getStock();
            });
        } catch (Exception e) {
            log.error("Inventory repair failed, mysql lock read error, productId={}", productId, e);
            return null;
        }
    }

    private boolean isNumeric(String value) {
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
