package com.freshtrace.common.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * RocketMQ Consumer 基类封装。
 * <p>
 * 具体业务 Consumer（如订单超时取消）继承本类，实现 {@link #handleMessage}，
 * 并在自身构造器内通过 super 指定 group/topic/tag。默认语义：
 * 消息处理抛异常时返回 RECONSUME_LATER，由 RocketMQ 按重试策略重投。
 */
@Slf4j
public abstract class AbstractRocketMqConsumer implements InitializingBean, DisposableBean {

    private final DefaultMQPushConsumer consumer;

    protected AbstractRocketMqConsumer(String nameServer, String group, String topic, String tag) {
        this.consumer = new DefaultMQPushConsumer(group);
        this.consumer.setNamesrvAddr(nameServer);
        try {
            this.consumer.subscribe(topic, tag);
        } catch (MQClientException e) {
            throw new IllegalStateException("MQ consumer 订阅失败: " + topic, e);
        }
    }

    @Override
    public void afterPropertiesSet() throws MQClientException {
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    handleMessage(msg);
                } catch (Exception e) {
                    log.error("MQ message handle failed, topic={}, tag={}, keys={}, msgId={}",
                            msg.getTopic(), msg.getTags(), msg.getKeys(), msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("RocketMQ consumer started, group={}, topic={}",
                consumer.getConsumerGroup(), consumer.getSubscription());
    }

    @Override
    public void destroy() {
        consumer.shutdown();
    }

    /**
     * 处理单条消息。业务实现必须保证幂等（消息可能因重试重复投递）。
     */
    public abstract void handleMessage(MessageExt message);
}
