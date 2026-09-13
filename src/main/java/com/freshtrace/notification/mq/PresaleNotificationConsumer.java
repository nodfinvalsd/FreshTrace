package com.freshtrace.notification.mq;

import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTags;
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
 * 预售成熟通知消费者：订阅 PRESALE_NOTIFY（Phase 7 发送，Phase 9 消费）。
 * <p>
 * 消息体携带该预售的全部预约用户，消费者批量写入站内信；逐条 dedup_key 幂等。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class PresaleNotificationConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationMessageSupport messageSupport;

    public PresaleNotificationConsumer(NotificationMessageSupport messageSupport,
                                       ObjectMapper objectMapper,
                                       @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                                       @Value("${rocketmq.consumer.notification-presale-group:freshtrace-notification-presale-consumer}") String group) {
        super(nameServer, group, MqTopics.PRESALE_NOTIFY, MqTags.PRESALE_MATURED);
        this.messageSupport = messageSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("PRESALE_NOTIFY parse failed, msgId={}, keys={}", message.getMsgId(), message.getKeys(), e);
            return;
        }
        messageSupport.handlePresaleMatured(node);
    }
}
