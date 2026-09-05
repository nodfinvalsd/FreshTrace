package com.freshtrace.trade.support;

import com.freshtrace.common.redis.RedisLuaExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存预扣/回补支持。
 * <p>
 * Redis 只负责预扣（快速拦截），MySQL 才是最终库存事实来源（事务内条件扣减）。
 * 预扣成功但 MySQL 事务失败时调用 {@link #compensate} 回补，
 * 回补失败必须记录 ERROR，交由后续库存对账任务兜底修复。
 * <p>
 * 冷启动策略：key 不存在视为库存 0，业务层先用 MySQL 当前库存 SETNX 初始化，
 * 保证不会因 Redis 冷启动而凭空超卖（见 {@link #initIfAbsent}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockDeductionSupport {

    public static final String STOCK_KEY_PREFIX = "stock:product:";

    /**
     * 批量库存预扣 Lua（原子）：
     * - 所有 key 必须存在（否则返回 -1，冷启动交由业务层初始化后重试）
     * - 全部数量合法且库存充足才统一 DECRBY（任一不足整批失败，返回 0）
     * - 参数非法返回 -2
     * - 成功返回 1
     */
    private static final String BATCH_DEDUCT_SCRIPT = """
            local n = #KEYS
            for i = 1, n do
                if redis.call('EXISTS', KEYS[i]) == 0 then
                    return -1
                end
            end
            for i = 1, n do
                local stock = tonumber(redis.call('GET', KEYS[i]))
                local need = tonumber(ARGV[i])
                if need == nil or need <= 0 then
                    return -2
                end
                if stock == nil or stock < need then
                    return 0
                end
            end
            for i = 1, n do
                redis.call('DECRBY', KEYS[i], ARGV[i])
            end
            return 1
            """;

    private final RedisLuaExecutor redisLuaExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    public String keyOf(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    /**
     * 冷启动/对齐初始化：仅当 key 不存在时，用 MySQL 当前库存初始化（SETNX 不覆盖已有值）。
     */
    public void initIfAbsent(Long productId, int mysqlStock) {
        stringRedisTemplate.opsForValue().setIfAbsent(keyOf(productId), String.valueOf(mysqlStock));
    }

    /**
     * 批量原子预扣。
     *
     * @return 1=成功；0=库存不足；-1=存在未初始化的 key；-2=参数非法
     */
    public int deductBatch(List<Long> productIds, List<Integer> quantities) {
        List<String> keys = productIds.stream().map(this::keyOf).toList();
        Object[] args = quantities.stream().map(String::valueOf).toArray();
        Long result = redisLuaExecutor.execute(BATCH_DEDUCT_SCRIPT, Long.class, keys, args);
        return result == null ? -2 : result.intValue();
    }

    /**
     * 回补 Redis 库存（MySQL 事务失败补偿）。回补失败仅记录 ERROR，由对账任务兜底。
     */
    public void compensate(Long productId, int quantity, String orderNo) {
        try {
            stringRedisTemplate.opsForValue().increment(keyOf(productId), quantity);
        } catch (Exception e) {
            log.error("Redis stock compensate failed, productId={}, quantity={}, orderNo={}",
                    productId, quantity, orderNo, e);
        }
    }

    /**
     * 恢复 Redis 库存（取消/退款成功后，MySQL 事务提交后调用）。
     * 失败仅记录 ERROR，由库存对账任务兜底修复。重复调用防护由调用方的事务抢占保证。
     */
    public void restore(Long productId, int quantity, String orderNo) {
        try {
            stringRedisTemplate.opsForValue().increment(keyOf(productId), quantity);
        } catch (Exception e) {
            log.error("Redis stock restore failed, productId={}, quantity={}, orderNo={}",
                    productId, quantity, orderNo, e);
        }
    }
}
