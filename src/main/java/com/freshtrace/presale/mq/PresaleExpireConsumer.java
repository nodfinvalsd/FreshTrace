package com.freshtrace.presale.mq;

import com.freshtrace.common.mq.AbstractRocketMqConsumer;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.presale.service.PresaleExpireService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PRESALE_EXPIRE（设置预售时按 presale_end - now 发送的定时消息）消费者。
 * <p>
 * 幂等语义：
 * - 坏消息（解析失败 / 缺 presaleId）→ 记日志直接消费，不重试；
 * - 预售不存在 / 非进行中 / 未到截止 → expireIfDue 内部 no-op 或重投，直接 ACK；
 * - 基础设施异常抛出 → RECONSUME_LATER 重试。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class PresaleExpireConsumer extends AbstractRocketMqConsumer {

    private final ObjectMapper objectMapper;
    private final PresaleExpireService presaleExpireService;

    public PresaleExpireConsumer(ObjectMapper objectMapper,
                                 PresaleExpireService presaleExpireService,
                                 @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
                                 @Value("${rocketmq.consumer.presale-expire-group:freshtrace-presale-expire-consumer}") String group) {
        super(nameServer, group, MqTopics.PRESALE_EXPIRE, MqTags.PRESALE_EXPIRED);
        this.objectMapper = objectMapper;
        this.presaleExpireService = presaleExpireService;
    }

    @Override
    public void handleMessage(MessageExt message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("PRESALE_EXPIRE message parse failed, msgId={}, keys={}", message.getMsgId(), message.getKeys(), e);
            return;
        }
        long presaleId = node.path("presaleId").asLong(-1L);
        if (presaleId <= 0) {
            log.warn("PRESALE_EXPIRE message missing presaleId, msgId={}, keys={}", message.getMsgId(), message.getKeys());
            return;
        }
        presaleExpireService.expireIfDue(presaleId);
    }
}
