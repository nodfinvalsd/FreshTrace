package com.freshtrace.common.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ Producer 最小可用封装（基于现有 rocketmq-client 5.5.1 裸客户端，不引入 starter）。
 * <p>
 * 通过 rocketmq.enabled 控制开关：测试环境默认关闭，避免无 MQ 集群时阻塞上下文启动。
 * 调用方（业务 Service）应在 MySQL 事务提交后（afterCommit）发送消息。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RocketMqProducer implements InitializingBean, DisposableBean {

    private final DefaultMQProducer producer;

    public RocketMqProducer(
            @Value("${rocketmq.name-server:localhost:9876}") String nameServer,
            @Value("${rocketmq.producer.group:freshtrace-producer-group}") String group,
            @Value("${rocketmq.producer.send-timeout-ms:3000}") int sendTimeoutMs,
            @Value("${rocketmq.producer.retry-times-when-send-failed:2}") int retryTimes) {
        this.producer = new DefaultMQProducer(group);
        this.producer.setNamesrvAddr(nameServer);
        this.producer.setSendMsgTimeout(sendTimeoutMs);
        this.producer.setRetryTimesWhenSendFailed(retryTimes);
        this.producer.setRetryTimesWhenSendAsyncFailed(0);
    }

    @Override
    public void afterPropertiesSet() throws MQClientException {
        producer.start();
        log.info("RocketMQ producer started, group={}, nameServer={}",
                producer.getProducerGroup(), producer.getNamesrvAddr());
    }

    @Override
    public void destroy() {
        producer.shutdown();
        log.info("RocketMQ producer shutdown, group={}", producer.getProducerGroup());
    }

    /**
     * 发送普通消息。
     */
    public SendResult send(String topic, String tag, String keys, String body) {
        return send(new Message(topic, tag, keys, body.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 发送定时消息（RocketMQ 5.x broker 的 timer message 能力，支持任意毫秒级延迟）。
     * 用于订单 30min 超时取消等场景。
     */
    public SendResult sendDelayed(String topic, String tag, String keys, String body, long delayMillis) {
        Message message = new Message(topic, tag, keys, body.getBytes(StandardCharsets.UTF_8));
        message.setDeliverTimeMs(System.currentTimeMillis() + delayMillis);
        return send(message);
    }

    private SendResult send(Message message) {
        try {
            SendResult result = producer.send(message);
            log.info("MQ message sent, topic={}, tag={}, keys={}, msgId={}",
                    message.getTopic(), message.getTags(), message.getKeys(), result.getMsgId());
            return result;
        } catch (Exception e) {
            log.error("MQ message send failed, topic={}, tag={}, keys={}",
                    message.getTopic(), message.getTags(), message.getKeys(), e);
            throw new IllegalStateException("MQ 消息发送失败: " + message.getTopic(), e);
        }
    }
}
