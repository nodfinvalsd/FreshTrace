package com.freshtrace.fulfillment.mq;

import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.fulfillment.service.FulfillmentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AUTO_CONFIRM（发货后 7 天延迟消息）消费者。
 * <p>
 * 幂等语义：
 * - 坏消息（JSON 解析失败 / 缺 subOrderNo）→ 记日志直接消费掉，不重试；
 * - 子订单不存在 / 已 FINISHED / 其他非 PENDING_RECEIVE 状态 → autoConfirmSubOrder 内部幂等 no-op，直接 ACK；
 * - 仅 PENDING_RECEIVE 才条件更新确认；基础设施异常（DB 不可用等）抛出 → RECONSUME_LATER 重试。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AutoConfirmConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final FulfillmentService fulfillmentService;

    public AutoConfirmConsumer(ObjectMapper objectMapper,
                               FulfillmentService fulfillmentService,
                               @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                               @Value("${rocketmq.consumer.auto-confirm-group:freshtrace-auto-confirm-consumer}") String group) {
        super(nameServer, group, MqTopics.AUTO_CONFIRM, MqTags.ORDER_SHIPPED);
        this.objectMapper = objectMapper;
        this.fulfillmentService = fulfillmentService;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("AUTO_CONFIRM message parse failed, msgId={}, keys={}", message.getMsgId(), message.getKeys(), e);
            return;
        }
        String subOrderNo = node.path("subOrderNo").asText("");
        if (subOrderNo.isBlank()) {
            log.warn("AUTO_CONFIRM message missing subOrderNo, msgId={}, keys={}", message.getMsgId(), message.getKeys());
            return;
        }
        fulfillmentService.autoConfirmSubOrder(subOrderNo);
    }
}
