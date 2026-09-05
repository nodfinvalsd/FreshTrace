package com.freshtrace.common.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RocketMQ 基础设施验证：依赖本地 docker-compose 的 namesrv/broker（autoCreateTopicEnable=true）。
 * 通过 @TestPropertySource 覆盖 test 环境的 rocketmq.enabled=false，启用真实 Producer。
 * 测试环境无 MQ 集群时本测试会失败，属预期（与 MySQL/Redis 测试依赖一致）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"rocketmq.enabled=true"})
class RocketMqInfrastructureTest {

    private static final String NAME_SERVER = "localhost:9876";

    @Autowired
    private RocketMqProducer rocketMqProducer;

    @Test
    void sendAndConsumeNormalMessage() throws Exception {
        String topic = "freshtrace-infra-test-" + System.currentTimeMillis();
        String tag = MqTags.ORDER_CREATED;
        String body = "{\"orderNo\":\"T123\"}";

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        AbstractRocketMqConsumer consumer = new AbstractRocketMqConsumer(
                NAME_SERVER, "freshtrace-infra-test-consumer-" + System.currentTimeMillis(), topic, tag) {
            @Override
            public void handleMessage(MessageExt message) {
                received.offer(new String(message.getBody(), StandardCharsets.UTF_8));
            }
        };
        ((InitializingBean) consumer).afterPropertiesSet();
        try {
            Thread.sleep(2000L);
            SendResult result = rocketMqProducer.send(topic, tag, "key-1", body);
            assertThat(result.getMsgId()).isNotBlank();
            assertThat(received.poll(30, TimeUnit.SECONDS)).isEqualTo(body);
        } finally {
            ((DisposableBean) consumer).destroy();
        }
    }

    @Test
    void sendDelayedMessageDeliversAfterDelay() throws Exception {
        String topic = "freshtrace-infra-delay-test-" + System.currentTimeMillis();
        String tag = MqTags.STOCK_CHANGED;
        String body = "{\"productId\":100,\"change\":-1}";

        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        AbstractRocketMqConsumer consumer = new AbstractRocketMqConsumer(
                NAME_SERVER, "freshtrace-infra-delay-consumer-" + System.currentTimeMillis(), topic, tag) {
            @Override
            public void handleMessage(MessageExt message) {
                received.offer(new String(message.getBody(), StandardCharsets.UTF_8));
            }
        };
        ((InitializingBean) consumer).afterPropertiesSet();
        try {
            rocketMqProducer.sendDelayed(topic, tag, "key-1", body, 3000L);
            assertThat(received.poll(2, TimeUnit.SECONDS)).as("3s 延迟消息不应提前投递").isNull();
            assertThat(received.poll(30, TimeUnit.SECONDS)).isEqualTo(body);
        } finally {
            ((DisposableBean) consumer).destroy();
        }
    }
}
