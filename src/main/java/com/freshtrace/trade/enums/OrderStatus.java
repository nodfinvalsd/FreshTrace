package com.freshtrace.trade.enums;

import lombok.Getter;

/**
 * 主订单状态。code 与 DDL 注释一致（0-5）。
 * 主订单状态由子订单履约进度推导 + 支付/取消事件驱动。
 */
@Getter
public enum OrderStatus {

    PENDING_PAY(0, "待支付"),
    PAID(1, "已支付"),
    PARTIAL_SHIPPED(2, "部分发货"),
    ALL_SHIPPED(3, "全部发货"),
    FINISHED(4, "已完成"),
    CANCELLED(5, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
