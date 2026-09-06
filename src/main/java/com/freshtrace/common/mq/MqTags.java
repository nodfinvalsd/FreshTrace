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

    /** 子订单发货 */
    public static final String ORDER_SHIPPED = "order_shipped";
}
