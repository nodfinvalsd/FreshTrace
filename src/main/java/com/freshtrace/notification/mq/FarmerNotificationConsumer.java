package com.freshtrace.notification.mq;

import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.notification.support.NotificationMessageSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 果农侧通知消费者：订阅 FARMER_NOTIFICATION（下单/支付/取消），写入果农站内信。
 * <p>
 * 幂等语义（与项目其他 Consumer 一致）：
 * - 坏消息（JSON 解析失败 / 缺关键字段）→ 记日志直接 ACK，不重试；
 * - 基础设施异常抛出 → RECONSUME_LATER 由 MQ 重投；重复投递由 dedup_key 唯一索引兜底。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FarmerNotificationConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationMessageSupport messageSupport;

    public FarmerNotificationConsumer(NotificationMessageSupport messageSupport,
                                      ObjectMapper objectMapper,
                                      @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                                      @Value("${rocketmq.consumer.notification-farmer-group:freshtrace-notification-farmer-consumer}") String group) {
        // 订阅该 topic 下全部 tag（order_created / order_paid / order_cancelled）
        super(nameServer, group, MqTopics.FARMER_NOTIFICATION, "*");
        this.messageSupport = messageSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("FARMER_NOTIFICATION parse failed, msgId={}, tag={}", message.getMsgId(), message.getTags(), e);
            return;
        }
        messageSupport.handleFarmerEvent(message.getTags(), node);
    }
}
