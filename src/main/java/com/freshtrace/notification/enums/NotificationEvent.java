package com.freshtrace.notification.enums;

import com.freshtrace.common.mq.MqTags;
import lombok.Getter;

/**
 * 通知事件：业务 MQ 事件的 Tag 与通知类型、标题的映射。
 * <p>
 * tag 与 {@link MqTags} 常量一一对应，消费者按 tag 解析后据此定型；
 * 正文（content）因事件参数不同，由 {@code NotificationMessageSupport} 在落库前组装。
 */
@Getter
public enum NotificationEvent {

    /** 下单成功 → 果农 */
    ORDER_CREATED(MqTags.ORDER_CREATED, NotificationType.ORDER, "新订单提醒"),
    /** 支付成功 → 果农 */
    ORDER_PAID(MqTags.ORDER_PAID, NotificationType.ORDER, "订单已支付"),
    /** 订单取消/退款 → 果农 */
    ORDER_CANCELLED(MqTags.ORDER_CANCELLED, NotificationType.ORDER, "订单已取消"),
    /** 果农发货 → 买家 */
    ORDER_SHIPPED(MqTags.ORDER_SHIPPED, NotificationType.ORDER, "订单已发货"),
    /** 确认收货 → 果农 */
    ORDER_RECEIVED(MqTags.ORDER_RECEIVED, NotificationType.ORDER, "订单已确认收货"),
    /** 果农回复评价 → 买家 */
    REVIEW_REPLIED(MqTags.REVIEW_REPLIED, NotificationType.ORDER, "果农回复了您的评价"),
    /** 预售成熟 → 预约买家 */
    PRESALE_MATURED(MqTags.PRESALE_MATURED, NotificationType.PRESALE, "预售成熟提醒"),
    /** 售后申请待处理 → 果农（Phase 10） */
    REFUND_APPLIED(MqTags.REFUND_APPLIED, NotificationType.ORDER, "退款申请待处理"),
    /** 果农同意退款 → 买家（Phase 10） */
    REFUND_FARMER_APPROVED(MqTags.REFUND_FARMER_APPROVED, NotificationType.ORDER, "退款申请已同意"),
    /** 果农拒绝退款 → 买家（Phase 10） */
    REFUND_FARMER_REJECTED(MqTags.REFUND_FARMER_REJECTED, NotificationType.ORDER, "退款申请被拒绝"),
    /** 平台仲裁同意退款 → 买家（Phase 10） */
    REFUND_ARBITRATION_APPROVED(MqTags.REFUND_ARBITRATION_APPROVED, NotificationType.ORDER, "平台仲裁退款成立"),
    /** 平台仲裁驳回退款 → 买家（Phase 10） */
    REFUND_ARBITRATION_REJECTED(MqTags.REFUND_ARBITRATION_REJECTED, NotificationType.ORDER, "平台仲裁驳回退款");

    private final String tag;
    private final NotificationType type;
    private final String title;

    NotificationEvent(String tag, NotificationType type, String title) {
        this.tag = tag;
        this.type = type;
        this.title = title;
    }

    /**
     * 按 MQ Tag 解析事件；未知 tag 返回 null，由调用方记录并跳过。
     */
    public static NotificationEvent fromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        for (NotificationEvent event : values()) {
            if (event.tag.equals(tag)) {
                return event;
            }
        }
        return null;
    }
}
