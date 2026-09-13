package com.freshtrace.common.mq;

/**
 * RocketMQ Message Tag 常量统一管理。
 */
public final class MqTags {

    private MqTags() {
    }

    /** 订单创建成功 */
    public static final String ORDER_CREATED = "order_created";

    /** 支付成功 */
    public static final String ORDER_PAID = "order_paid";

    /** 订单取消 */
    public static final String ORDER_CANCELLED = "order_cancelled";

    /** 库存变更 */
    public static final String STOCK_CHANGED = "stock_changed";

    /** 子订单发货（AUTO_CONFIRM 延迟消息与 NOTIFICATION 发货通知共用） */
    public static final String ORDER_SHIPPED = "order_shipped";

    /** 确认收货 */
    public static final String ORDER_RECEIVED = "order_received";

    /** 果农回复评价 */
    public static final String REVIEW_REPLIED = "review_replied";

    /** 预售到期 */
    public static final String PRESALE_EXPIRED = "presale_expired";

    /** 预售成熟通知 */
    public static final String PRESALE_MATURED = "presale_matured";

    /** 售后申请待处理（买家申请 → 果农，Phase 10） */
    public static final String REFUND_APPLIED = "refund_applied";

    /** 果农同意退款（→ 买家，Phase 10） */
    public static final String REFUND_FARMER_APPROVED = "refund_farmer_approved";

    /** 果农拒绝退款（→ 买家，Phase 10） */
    public static final String REFUND_FARMER_REJECTED = "refund_farmer_rejected";

    /** 平台仲裁同意退款（→ 买家，Phase 10） */
    public static final String REFUND_ARBITRATION_APPROVED = "refund_arbitration_approved";

    /** 平台仲裁驳回退款（→ 买家，Phase 10） */
    public static final String REFUND_ARBITRATION_REJECTED = "refund_arbitration_rejected";

    /** 商品索引 upsert（Phase 11 ES 同步） */
    public static final String PRODUCT_UPSERT = "product_upsert";

    /** 商品索引删除（Phase 11 ES 同步） */
    public static final String PRODUCT_DELETE = "product_delete";
}
