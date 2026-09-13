package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.notification.entity.Notification;
import com.freshtrace.notification.mapper.NotificationMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息通知真实 MQ 全链路测试（Phase 9 Day 3 联调）。
 * <p>
 * 通过 {@code rocketmq.enabled=true} 启动真实 Producer 与三个通知消费者，
 * 覆盖「生产 → topic → 消费 → 事件映射 → 落库」完整链路（测试环境依赖本地 docker-compose 的 RocketMQ）。
 * 与 {@link NotificationIntegrationTest}（MQ 关闭，直接调用消费侧映射）互补。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"rocketmq.enabled=true"})
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class NotificationMqIntegrationTest {

    private static final long AWAIT_TIMEOUT_MS = 90_000L;

    @Autowired
    private RocketMqProducer rocketMqProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void endToEndMqNotificationFlow() throws Exception {
        // 等待消费者订阅并就绪：DefaultMQPushConsumer 默认从 LAST_OFFSET 起消费，
        // 若发送早于 offset 初始化，消息会被跳过（全量跑时 context 重建后尤其明显）。
        Thread.sleep(3000L);

        // 1. 果农事件：下单/支付通知 → FARMER_NOTIFICATION → 果农站内信
        Long farmerId = createFarmer();
        Long farmerUserId = farmerMapper.selectById(farmerId).getUserId();
        long subOrderId = seq.incrementAndGet();
        send(MqTopics.FARMER_NOTIFICATION, MqTags.ORDER_PAID, "SO-" + subOrderId,
                json(Map.of("farmerId", farmerId, "subOrderId", subOrderId, "subOrderNo", "SO-" + subOrderId)));
        awaitNotificationCount(farmerUserId, 1);

        // 2. 买家事件：发货通知 → NOTIFICATION → 买家站内信
        long buyerId = 910001L;
        long shipSubOrderId = seq.incrementAndGet();
        send(MqTopics.NOTIFICATION, MqTags.ORDER_SHIPPED, "SO-" + shipSubOrderId,
                json(Map.of("orderId", 1, "subOrderId", shipSubOrderId, "subOrderNo", "SO-" + shipSubOrderId,
                        "buyerId", buyerId, "logisticsCompany", "顺丰", "logisticsNo", "SF999")));
        awaitNotificationCount(buyerId, 1);
        assertThat(notificationMapper.selectList(byUser(buyerId)).get(0).getContent()).contains("SF999");

        // 3. 预售成熟：一对多批量通知 → PRESALE_NOTIFY → 预约用户站内信
        long userA = 910002L;
        long userB = 910003L;
        long presaleId = seq.incrementAndGet();
        send(MqTopics.PRESALE_NOTIFY, MqTags.PRESALE_MATURED, String.valueOf(presaleId),
                json(Map.of("presaleId", presaleId, "productId", 1, "userIds", new long[]{userA, userB})));
        awaitNotificationCount(userA, 1);
        awaitNotificationCount(userB, 1);
    }

    private void send(String topic, String tag, String keys, String body) {
        rocketMqProducer.send(topic, tag, keys, body);
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private LambdaQueryWrapper<Notification> byUser(long userId) {
        return new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId);
    }

    /**
     * 轮询等待通知落库（消费者异步处理），超时判定失败。
     */
    private void awaitNotificationCount(long userId, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        long count = 0;
        while (System.currentTimeMillis() < deadline) {
            count = notificationMapper.selectCount(byUser(userId));
            if (count >= expected) {
                return;
            }
            Thread.sleep(500L);
        }
        throw new AssertionError("expect >= " + expected + " notification for user " + userId + " but got " + count);
    }

    private Long createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("mq_farmer_" + n);
        user.setPasswordHash("x");
        user.setPhone("139" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("李四");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("MQ测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村2号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return farmer.getId();
    }
}
