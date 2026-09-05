package com.freshtrace.trade.support;

import lombok.Getter;

/**
 * 一轮库存对账的统计结果。
 */
@Getter
public class ReconciliationResult {

    /** 扫描商品数 */
    private int scanned;

    /** 一致数量 */
    private int consistent;

    /** Redis key 缺失数量 */
    private int missing;

    /** 库存不一致/异常数量（含 Redis 非数字、MySQL 负库存） */
    private int mismatch;

    /** 修复成功数量 */
    private int repaired;

    /** 修复失败数量 */
    private int repairFailed;

    public void incrementScanned() {
        scanned++;
    }

    public void incrementMissing() {
        missing++;
    }

    public void incrementMismatch() {
        mismatch++;
    }

    public void recordRepair(com.freshtrace.trade.enums.ReconcileStatus status) {
        switch (status) {
            case CONSISTENT -> consistent++;
            case REPAIRED -> repaired++;
            case REPAIR_FAILED -> repairFailed++;
        }
    }

    @Override
    public String toString() {
        return "scanned=" + scanned
                + ", consistent=" + consistent
                + ", missing=" + missing
                + ", mismatch=" + mismatch
                + ", repaired=" + repaired
                + ", repairFailed=" + repairFailed;
    }
}
