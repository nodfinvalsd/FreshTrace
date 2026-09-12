package com.freshtrace.presale.support;

import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预售 MQ 消息发送支撑（Phase 7）。
 * <p>
 * - 到期延迟消息：PRESALE_EXPIRE / PRESALE_EXPIRED，delay = presale_end - now；
 * - 成熟通知：PRESALE_NOTIFY / PRESALE_MATURED，Phase 9 通知模块消费后写站内信。<br>
 * MQ 关闭（测试环境）时静默跳过；发送失败仅记录 ERROR，不影响已提交的业务事实，
 * 到期由 {@code PresaleExpireTask} 定时兜底。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresaleMessageSupport {

    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;

    /**
     * 发送到期延迟消息。presale_end 已过或 MQ 关闭时跳过。
     */
    public void sendExpire(Long presaleId, Long productId, LocalDateTime presaleEnd) {
        long delayMs = Duration.between(LocalDateTime.now(), presaleEnd).toMillis();
        if (delayMs <= 0) {
            log.warn("presale end is not in the future, skip expire message, presaleId={}, presaleEnd={}",
                    presaleId, presaleEnd);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presaleId", presaleId);
        payload.put("productId", productId);
        send(MqTopics.PRESALE_EXPIRE, MqTags.PRESALE_EXPIRED, String.valueOf(presaleId), payload, delayMs,
                "presale expire");
    }

    /**
     * 发送成熟通知消息（批量携带预约用户）。
     */
    public void sendMatureNotification(Long presaleId, Long productId, List<Long> userIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presaleId", presaleId);
        payload.put("productId", productId);
        payload.put("userIds", userIds);
        send(MqTopics.PRESALE_NOTIFY, MqTags.PRESALE_MATURED, String.valueOf(presaleId), payload, 0L,
                "presale mature notification");
    }

    private void send(String topic, String tag, String key, Map<String, Object> payload, long delayMs, String desc) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip {} message, key={}", desc, key);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            if (delayMs > 0) {
                producer.sendDelayed(topic, tag, key, body, delayMs);
            } else {
                producer.send(topic, tag, key, body);
            }
        } catch (Exception e) {
            log.error("{} message send failed, key={}", desc, key, e);
        }
    }
}
