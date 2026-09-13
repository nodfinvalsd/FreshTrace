package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.notification.entity.Notification;
import com.freshtrace.notification.enums.NotificationType;
import com.freshtrace.notification.mapper.NotificationMapper;
import com.freshtrace.notification.service.NotificationService;
import com.freshtrace.notification.support.NotificationMessageSupport;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 消息通知集成测试（Phase 9）。
 * <p>
 * 覆盖：列表分页/过滤、未读数、单条/全部已读、归属校验、dedup 幂等，
 * 以及事件 → 通知映射（果农事件、买家事件、预售成熟批量）。MQ 在测试环境关闭，
 * 故直接调用 {@link NotificationMessageSupport} 验证消费侧映射逻辑。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private NotificationMessageSupport messageSupport;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void listPaginationAndFilter() throws Exception {
        long userId = 900001L;
        notificationService.push(userId, NotificationType.ORDER, "订单通知A", "内容A", 100L, "order_paid:100:" + userId);
        notificationService.push(userId, NotificationType.ORDER, "订单通知B", "内容B", 101L, "order_created:101:" + userId);
        notificationService.push(userId, NotificationType.PRESALE, "预售通知", "内容C", 200L, "presale_matured:200:" + userId);

        JsonNode all = getJson("/notification/list", userId).path("data");
        assertThat(all.path("total").asLong()).isEqualTo(3);
        assertThat(all.path("records").get(0).path("typeDesc").asText()).isNotBlank();

        JsonNode orderOnly = getJson("/notification/list?type=1", userId).path("data");
        assertThat(orderOnly.path("total").asLong()).isEqualTo(2);

        JsonNode presaleOnly = getJson("/notification/list?type=2", userId).path("data");
        assertThat(presaleOnly.path("total").asLong()).isEqualTo(1);

        JsonNode unreadOnly = getJson("/notification/list?read=false", userId).path("data");
        assertThat(unreadOnly.path("total").asLong()).isEqualTo(3);
    }

    @Test
    void unreadCountAndReadFlow() throws Exception {
        long userId = 900001L;
        notificationService.push(userId, NotificationType.ORDER, "通知1", "内容1", 1L, "t1:1:" + userId);
        notificationService.push(userId, NotificationType.ORDER, "通知2", "内容2", 2L, "t2:2:" + userId);

        assertThat(getJson("/notification/unread-count", userId).path("data").asLong()).isEqualTo(2);

        long id = firstNotificationId(userId);
        mockMvc.perform(put("/notification/{id}/read", id)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(getJson("/notification/unread-count", userId).path("data").asLong()).isEqualTo(1);

        mockMvc.perform(put("/notification/read-all")
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(getJson("/notification/unread-count", userId).path("data").asLong()).isEqualTo(0);
    }

    @Test
    void markReadByOtherUserRejected() throws Exception {
        long owner = 900001L;
        notificationService.push(owner, NotificationType.ORDER, "通知", "内容", 1L, "t:1:" + owner);
        long id = firstNotificationId(owner);

        mockMvc.perform(put("/notification/{id}/read", id)
                        .header("Authorization", "Bearer " + token(900002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30062));
    }

    @Test
    void markReadNotFound() throws Exception {
        mockMvc.perform(put("/notification/{id}/read", 99999999L)
                        .header("Authorization", "Bearer " + token(900001L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30061));
    }

    @Test
    void duplicatePushIsIdempotent() {
        long userId = 900001L;
        String dedupKey = "order_paid:1:" + userId;
        notificationService.push(userId, NotificationType.ORDER, "通知", "内容", 1L, dedupKey);
        notificationService.push(userId, NotificationType.ORDER, "通知", "内容", 1L, dedupKey);

        assertThat(countByUser(userId)).isEqualTo(1);
    }

    @Test
    void pushBatchIsIdempotent() {
        long userA = 900001L;
        long userB = 900002L;
        List<Notification> batch = new ArrayList<>();
        batch.add(buildNotification(userA, "presale_matured:1:" + userA));
        batch.add(buildNotification(userB, "presale_matured:1:" + userB));
        // 与第一条 dedup_key 重复，应被忽略
        batch.add(buildNotification(userA, "presale_matured:1:" + userA));

        assertThat(notificationService.pushBatch(batch)).isEqualTo(2);
        assertThat(countByUser(userA)).isEqualTo(1);
        assertThat(countByUser(userB)).isEqualTo(1);
    }

    @Test
    void farmerEventMappedToNotification() throws Exception {
        Long farmerId = createFarmer();
        Long farmerUserId = farmerMapper.selectById(farmerId).getUserId();

        JsonNode node = objectMapper.readTree("""
                {"farmerId": %d, "subOrderId": 5001, "subOrderNo": "SO-5001", "orderId": 1}
                """.formatted(farmerId));
        messageSupport.handleFarmerEvent("order_paid", node);

        List<Notification> list = listByUser(farmerUserId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("订单已支付");
        assertThat(list.get(0).getType()).isEqualTo(NotificationType.ORDER.getCode());
        assertThat(list.get(0).getRelatedId()).isEqualTo(5001L);
    }

    @Test
    void businessShippedEventMappedToBuyer() throws Exception {
        long buyerId = 900003L;
        JsonNode node = objectMapper.readTree("""
                {"orderId": 1, "subOrderId": 6001, "subOrderNo": "SO-6001", "buyerId": %d,
                 "logisticsCompany": "顺丰", "logisticsNo": "SF123"}
                """.formatted(buyerId));
        messageSupport.handleBusinessEvent("order_shipped", node);

        List<Notification> list = listByUser(buyerId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("订单已发货");
        assertThat(list.get(0).getContent()).contains("SF123");
    }

    @Test
    void reviewRepliedEventMappedToBuyer() throws Exception {
        long buyerId = 900004L;
        JsonNode node = objectMapper.readTree("""
                {"reviewId": 7001, "buyerId": %d, "farmerId": 1, "productId": 1}
                """.formatted(buyerId));
        messageSupport.handleBusinessEvent("review_replied", node);

        List<Notification> list = listByUser(buyerId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("果农回复了您的评价");
        assertThat(list.get(0).getRelatedId()).isEqualTo(7001L);
    }

    @Test
    void presaleMaturedEventMappedToAllReservedUsers() throws Exception {
        long userA = 900005L;
        long userB = 900006L;
        JsonNode node = objectMapper.readTree("""
                {"presaleId": 8001, "productId": 1, "userIds": [%d, %d]}
                """.formatted(userA, userB));
        messageSupport.handlePresaleMatured(node);

        assertThat(listByUser(userA)).hasSize(1);
        assertThat(listByUser(userB)).hasSize(1);
        assertThat(listByUser(userA).get(0).getType()).isEqualTo(NotificationType.PRESALE.getCode());
        assertThat(listByUser(userA).get(0).getRelatedId()).isEqualTo(8001L);
    }

    private Notification buildNotification(long userId, String dedupKey) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(NotificationType.PRESALE.getCode());
        notification.setTitle("预售成熟提醒");
        notification.setContent("内容");
        notification.setRelatedId(1L);
        notification.setIsRead(0);
        notification.setDedupKey(dedupKey);
        return notification;
    }

    private long firstNotificationId(long userId) {
        return listByUser(userId).get(0).getId();
    }

    private long countByUser(long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));
    }

    private List<Notification> listByUser(long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getId));
    }

    private JsonNode getJson(String url, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Long createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
        user.setPhone("138" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return farmer.getId();
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }
}
