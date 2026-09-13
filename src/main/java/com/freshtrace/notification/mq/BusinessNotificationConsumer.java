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
 * 买家侧业务通知消费者：订阅 NOTIFICATION（发货/确认收货/评价回复）。
 * <p>
 * 事件接收人由消息体决定（发货→买家、确认收货→果农、评价回复→买家），
 * 消费者按 tag 分发，具体映射见 {@link NotificationMessageSupport#handleBusinessEvent}。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class BusinessNotificationConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationMessageSupport messageSupport;

    public BusinessNotificationConsumer(NotificationMessageSupport messageSupport,
                                        ObjectMapper objectMapper,
                                        @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                                        @Value("${rocketmq.consumer.notification-business-group:freshtrace-notification-business-consumer}") String group) {
        // 订阅该 topic 下全部 tag（order_shipped / order_received / review_replied）
        super(nameServer, group, MqTopics.NOTIFICATION, "*");
        this.messageSupport = messageSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("NOTIFICATION parse failed, msgId={}, tag={}", message.getMsgId(), message.getTags(), e);
            return;
        }
        messageSupport.handleBusinessEvent(message.getTags(), node);
    }
}
