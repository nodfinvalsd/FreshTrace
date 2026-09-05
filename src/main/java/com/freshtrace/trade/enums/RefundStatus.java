package com.freshtrace.trade.enums;

import lombok.Getter;

/**
 * 退款状态。code 与 DDL 注释一致（0-5）。
 * Phase 3-7 V1 只使用「已退款(4)」（整笔子订单全额模拟退款），
 * 果农同意/拒绝/客服仲裁等状态留给后续 Phase。
 */
@Getter
public enum RefundStatus {

    PENDING(0, "待处理"),
    FARMER_APPROVED(1, "果农同意"),
    FARMER_REJECTED(2, "果农拒绝"),
    ARBITRATION(3, "客服仲裁"),
    REFUNDED(4, "已退款"),
    REJECTED(5, "已驳回");

    private final int code;
    private final String desc;

    RefundStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RefundStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (RefundStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
