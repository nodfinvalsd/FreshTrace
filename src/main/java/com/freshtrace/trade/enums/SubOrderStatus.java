package com.freshtrace.trade.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;

/**
 * 子订单状态。code 与 DDL 注释一致（0-6）。
 * <p>
 * 状态转换白名单（对照 PRD v1.1 子订单状态机）：
 * <pre>
 * 待支付 ──支付成功──▶ 待发货 ──果农发货──▶ 待收货 ──确认收货──▶ 已完成
 *    │                  │                     │
 *    └──超时/取消────▶ 已取消                 └──申请退款──▶ 退款中 ──▶ 已退款
 *                        │                                        └──▶ 恢复待收货
 *                        ├──超时72h未发货──▶ 已取消（自动退款）
 *                        └──申请退款──▶ 退款中
 * 已完成 ──7天内售后──▶ 退款中（Phase 10 平台仲裁）
 * </pre>
 * 已取消 / 已退款 为终态。
 */
@Getter
public enum SubOrderStatus {

    PENDING_PAY(0, "待支付"),
    PENDING_SHIP(1, "待发货"),
    PENDING_RECEIVE(2, "待收货"),
    FINISHED(3, "已完成"),
    REFUNDING(4, "退款中"),
    REFUNDED(5, "已退款"),
    CANCELLED(6, "已取消");

    /**
     * 合法状态转换白名单。所有业务流转必须经 canTransitionTo 校验。
     * 自身到自身视为非法转换（不做无意义的状态更新）。
     */
    private static final Map<SubOrderStatus, EnumSet<SubOrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAY, EnumSet.of(PENDING_SHIP, CANCELLED),
            PENDING_SHIP, EnumSet.of(PENDING_RECEIVE, REFUNDING, CANCELLED),
            PENDING_RECEIVE, EnumSet.of(FINISHED, REFUNDING),
            REFUNDING, EnumSet.of(REFUNDED, PENDING_RECEIVE),
            FINISHED, EnumSet.of(REFUNDING),
            CANCELLED, EnumSet.noneOf(SubOrderStatus.class),
            REFUNDED, EnumSet.noneOf(SubOrderStatus.class)
    );

    private final int code;
    private final String desc;

    SubOrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(SubOrderStatus target) {
        if (target == null) {
            return false;
        }
        EnumSet<SubOrderStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public static SubOrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SubOrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
