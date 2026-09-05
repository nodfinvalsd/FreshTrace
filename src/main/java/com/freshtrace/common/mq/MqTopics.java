package com.freshtrace.common.mq;

/**
 * RocketMQ Topic 常量统一管理。
 */
public final class MqTopics {

    private MqTopics() {
    }

    /** 订单超时取消：下单成功后发送 30min 定时消息，由超时取消 Consumer 消费 */
    public static final String ORDER_TIMEOUT = "freshtrace-order-timeout-topic";

    /** 订单/库存事件：订单创建、支付、取消等状态变更事件（Phase 3 预留） */
    public static final String ORDER_STOCK = "freshtrace-order-stock-topic";

    /** 果农通知：下单/支付后通知果农（Phase 9 消费，Phase 3 只负责发送） */
    public static final String FARMER_NOTIFICATION = "freshtrace-farmer-notification-topic";

    /** ES 数据同步：商品销量/订单数据同步占位（Phase 3 只发送，不实现 ES 消费） */
    public static final String ES_SYNC = "freshtrace-es-sync-topic";
}
