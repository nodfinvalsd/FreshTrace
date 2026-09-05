package com.freshtrace.trade.service;

import com.freshtrace.trade.support.ReconciliationResult;

public interface InventoryReconciliationService {

    /**
     * 扫描全部未逻辑删除商品，检测 Redis/MySQL 库存差异并按安全策略修复。
     * <p>
     * - 基准：MySQL t_product.stock（最终事实），修复方向永远 MySQL → Redis；
     * - Redis key 缺失：按 MySQL 值初始化；
     * - Redis 非数字 / MySQL 负库存：不猜测、不自动修复，仅 ERROR 告警；
     * - 单商品异常不影响整轮扫描；
     * - 修复后二次校验（MySQL == Redis），并发扣减期间短暂不一致会重试收敛。
     */
    ReconciliationResult scanAndRepair();
}
