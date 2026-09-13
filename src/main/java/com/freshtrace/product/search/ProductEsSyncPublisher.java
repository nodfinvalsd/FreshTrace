package com.freshtrace.product.search;

import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品 ES 同步消息发布器。
 * <p>
 * 应用层双写：MySQL 事务提交后发送 ES_SYNC 消息，由 {@link com.freshtrace.product.search.mq.EsSyncConsumer}
 * 异步写入 ES。ES 关闭或 MQ 未启用时静默跳过，不影响商品主流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEsSyncPublisher {

    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;

    @Value("${elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    public void publishUpsert(Long productId) {
        publish(MqTags.PRODUCT_UPSERT, productId);
    }

    public void publishDelete(Long productId) {
        publish(MqTags.PRODUCT_DELETE, productId);
    }

    private void publish(String tag, Long productId) {
        if (!elasticsearchEnabled || productId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(tag, productId);
                }
            });
            return;
        }
        doSend(tag, productId);
    }

    private void doSend(String tag, Long productId) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip ES sync message, tag={}, productId={}", tag, productId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        try {
            producer.send(MqTopics.ES_SYNC, tag, String.valueOf(productId),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("ES sync message send failed, tag={}, productId={}", tag, productId, e);
        }
    }
}
