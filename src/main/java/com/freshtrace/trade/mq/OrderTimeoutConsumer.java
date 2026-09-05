package com.freshtrace.trade.mq;

import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.trade.service.OrderCancelService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ORDER_TIMEOUT（30min 延迟消息）消费者。
 * <p>
 * 幂等语义：
 * - 坏消息（JSON 解析失败 / 缺 orderId）→ 记日志直接消费掉，不重试；
 * - 订单不存在 / 已支付 / 已取消 → cancelByTimeout 内部 no-op，直接 ACK；
 * - 仅 PENDING_PAY 才真正取消；业务异常抛出 → RECONSUME_LATER（取消本身幂等，重试安全）。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OrderTimeoutConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCancelService orderCancelService;

    public OrderTimeoutConsumer(ObjectMapper objectMapper,
                                OrderCancelService orderCancelService,
                                @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                                @Value("${rocketmq.consumer.order-timeout-group:freshtrace-order-timeout-consumer}") String group) {
        super(nameServer, group, MqTopics.ORDER_TIMEOUT, MqTags.ORDER_CREATED);
        this.objectMapper = objectMapper;
        this.orderCancelService = orderCancelService;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("ORDER_TIMEOUT message parse failed, msgId={}, keys={}", message.getMsgId(), message.getKeys(), e);
            return;
        }
        long orderId = node.path("orderId").asLong(-1L);
        if (orderId <= 0) {
            log.warn("ORDER_TIMEOUT message missing orderId, msgId={}, keys={}", message.getMsgId(), message.getKeys());
            return;
        }
        orderCancelService.cancelByTimeout(orderId);
    }
}
