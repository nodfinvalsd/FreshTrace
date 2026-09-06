package com.freshtrace;

import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.mq.AutoConfirmConsumer;
import com.freshtrace.fulfillment.scheduler.AutoConfirmTask;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.mapper.UserMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 自动确认收货测试：MQ 延迟消息 Consumer（直接构造调用 handleMessage，不依赖真实 broker）
 * + Scheduler 兜底任务（通过 @TestPropertySource 启用 AutoConfirmTask Bean）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "fulfillment.auto-confirm.enabled=true")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AutoConfirmIntegrationTest {

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private AutoConfirmTask autoConfirmTask;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void consumerAutoConfirmsPendingReceive() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);

        AutoConfirmConsumer consumer = newConsumer();
        consumer.handleMessage(message(Map.of("subOrderNo", subOrderNo)));

        SubOrder latest = subOrderMapper.selectById(sub.getId());
        assertThat(latest.getStatus()).isEqualTo(3);
        assertThat(latest.getReceivedAt()).isNotNull();
        assertThat(orderMapper.selectById(latest.getOrderId()).getStatus()).isEqualTo(4);
    }

    @Test
    void consumerIdempotentOnRepeatedMessage() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);

        AutoConfirmConsumer consumer = newConsumer();
        MessageExt message = message(Map.of("subOrderNo", subOrderNo));
        consumer.handleMessage(message);
        SubOrder first = subOrderMapper.selectById(sub.getId());
        assertThat(first.getStatus()).isEqualTo(3);

        assertThatCode(() -> consumer.handleMessage(message)).doesNotThrowAnyException();
        SubOrder second = subOrderMapper.selectById(sub.getId());
        assertThat(second.getStatus()).isEqualTo(3);
        assertThat(second.getReceivedAt()).isEqualTo(first.getReceivedAt());
        assertThat(orderMapper.selectById(second.getOrderId()).getStatus()).isEqualTo(4);
    }

    @Test
    void consumerIgnoresMissingSubOrder() {
        AutoConfirmConsumer consumer = newConsumer();
        assertThatCode(() -> consumer.handleMessage(message(Map.of("subOrderNo", "S-not-exist"))))
                .doesNotThrowAnyException();
    }

    @Test
    void consumerIgnoresBadMessage() {
        AutoConfirmConsumer consumer = newConsumer();
        MessageExt bad = new MessageExt();
        bad.setTopic(MqTopics.AUTO_CONFIRM);
        bad.setTags(MqTags.ORDER_SHIPPED);
        bad.setBody("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatCode(() -> consumer.handleMessage(bad)).doesNotThrowAnyException();
    }

    @Test
    void consumerIgnoresMessageWithoutSubOrderNo() {
        AutoConfirmConsumer consumer = newConsumer();
        assertThatCode(() -> consumer.handleMessage(message(Map.of("orderId", 1))))
                .doesNotThrowAnyException();
    }

    @Test
    void consumerSkipsAlreadyFinished() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);
        LocalDateTime originalReceivedAt = LocalDateTime.now().minusHours(1).withNano(0);
        SubOrder update = new SubOrder();
        update.setId(sub.getId());
        update.setStatus(3);
        update.setReceivedAt(originalReceivedAt);
        subOrderMapper.updateById(update);

        newConsumer().handleMessage(message(Map.of("subOrderNo", subOrderNo)));

        SubOrder latest = subOrderMapper.selectById(sub.getId());
        assertThat(latest.getStatus()).isEqualTo(3);
        assertThat(latest.getReceivedAt()).isEqualTo(originalReceivedAt);
    }

    @Test
    void schedulerConfirmsExpiredOrders() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);
        setShippedAt(sub, LocalDateTime.now().minusDays(8).withNano(0));

        autoConfirmTask.autoConfirmExpired();

        SubOrder latest = subOrderMapper.selectById(sub.getId());
        assertThat(latest.getStatus()).isEqualTo(3);
        assertThat(latest.getReceivedAt()).isNotNull();
        assertThat(orderMapper.selectById(latest.getOrderId()).getStatus()).isEqualTo(4);
    }

    @Test
    void schedulerSkipsNotExpiredOrders() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);
        setShippedAt(sub, LocalDateTime.now().minusDays(6).withNano(0));

        autoConfirmTask.autoConfirmExpired();

        SubOrder latest = subOrderMapper.selectById(sub.getId());
        assertThat(latest.getStatus()).isEqualTo(2);
        assertThat(latest.getReceivedAt()).isNull();
        assertThat(orderMapper.selectById(latest.getOrderId()).getStatus()).isEqualTo(3);
    }

    @Test
    void schedulerConfirmsOnlyExpiredOnes() throws Exception {
        String expiredNo = createShippedSubOrder();
        String freshNo = createShippedSubOrder();
        SubOrder expired = subOrder(expiredNo);
        SubOrder fresh = subOrder(freshNo);
        setShippedAt(expired, LocalDateTime.now().minusDays(8).withNano(0));
        setShippedAt(fresh, LocalDateTime.now().minusDays(1).withNano(0));

        autoConfirmTask.autoConfirmExpired();

        assertThat(subOrderMapper.selectById(expired.getId()).getStatus()).isEqualTo(3);
        assertThat(subOrderMapper.selectById(fresh.getId()).getStatus()).isEqualTo(2);
    }

    @Test
    void mqAndSchedulerConcurrentConfirmIsSafe() throws Exception {
        String subOrderNo = createShippedSubOrder();
        SubOrder sub = subOrder(subOrderNo);
        setShippedAt(sub, LocalDateTime.now().minusDays(8).withNano(0));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                try {
                    start.await();
                    newConsumer().handleMessage(message(Map.of("subOrderNo", subOrderNo)));
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            });
            executor.submit(() -> {
                try {
                    start.await();
                    autoConfirmTask.autoConfirmExpired();
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            });
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        SubOrder latest = subOrderMapper.selectById(sub.getId());
        assertThat(latest.getStatus()).isEqualTo(3);
        assertThat(latest.getReceivedAt()).isNotNull();
        assertThat(orderMapper.selectById(latest.getOrderId()).getStatus()).isEqualTo(4);
    }

    private AutoConfirmConsumer newConsumer() {
        return new AutoConfirmConsumer(objectMapper, fulfillmentService, "localhost:9876", "test-consumer-group");
    }

    private MessageExt message(Map<String, Object> payload) throws Exception {
        MessageExt message = new MessageExt();
        message.setTopic(MqTopics.AUTO_CONFIRM);
        message.setTags(MqTags.ORDER_SHIPPED);
        message.setBody(objectMapper.writeValueAsBytes(payload));
        return message;
    }

    private void setShippedAt(SubOrder sub, LocalDateTime shippedAt) {
        SubOrder update = new SubOrder();
        update.setId(sub.getId());
        update.setShippedAt(shippedAt);
        subOrderMapper.updateById(update);
    }

    private SubOrder subOrder(String subOrderNo) {
        return subOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getSubOrderNo, subOrderNo));
    }

    private String createShippedSubOrder() {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);

        SubOrder sub = subOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getOrderId, orderIdByNo(orderNo))).get(0);
        ShipDTO dto = new ShipDTO();
        dto.setLogisticsCompany("顺丰");
        dto.setLogisticsNo("SF123");
        fulfillmentService.ship(farmer.farmerId(), sub.getSubOrderNo(), dto);
        return sub.getSubOrderNo();
    }

    private long orderIdByNo(String orderNo) {
        return orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)).getId();
    }

    private String createOrder(long userId, long addressId, List<Long> cartIds) {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(cartIds);
        return orderService.createOrder(userId, dto, "tok-" + UUID.randomUUID()).getOrderNo();
    }

    private void pay(long userId, String orderNo) {
        PayOrderDTO dto = new PayOrderDTO();
        dto.setOrderNo(orderNo);
        paymentService.pay(userId, dto, "tok-" + UUID.randomUUID());
    }

    private FarmerRef createFarmer() {
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
        return new FarmerRef(farmer.getId(), user.getId());
    }

    private long createProduct(long farmerId, String price, int stock, String title) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("海南芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle(title);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setUnit("斤");
        product.setMainImage("http://img/x.png");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long createAddress(long userId, String detail) {
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13812345678");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail(detail);
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    private long addCart(long userId, long productId, int quantity) {
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSpecSnapshot("");
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        return cart.getId();
    }
}
