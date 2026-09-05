package com.freshtrace.trade.enums;

import lombok.Getter;

/**
 * 单商品库存对账结果。
 */
@Getter
public enum ReconcileStatus {

    /** Redis 与 MySQL 一致，无需修复 */
    CONSISTENT,
    /** 已修复（Redis 已对齐 MySQL），并通过二次校验 */
    REPAIRED,
    /** 修复失败（异常值 / 写入失败 / 校验失败），需人工关注 */
    REPAIR_FAILED
}
