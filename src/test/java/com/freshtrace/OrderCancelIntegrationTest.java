package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.mq.OrderTimeoutConsumer;
import com.freshtrace.trade.service.OrderCancelService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderCancelIntegrationTest {

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
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductAttributeMapper productAttributeMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderCancelService orderCancelService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void a1CancelPendingPayOrderRestoresStock() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);

        mockMvc.perform(post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT cancelled_at FROM t_order", java.sql.Timestamp.class)).isNotNull();
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(6);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void a2CancelPaidOrderFails() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        pay(userId, orderNo);

        mockMvc.perform(post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30027));

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
    }

    @Test
    void a3OtherUserCannotCancel() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));

        mockMvc.perform(post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(900002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30026));

        assertThat(queryInt("SELECT status FROM t_order")).isZero();
    }

    @Test
    void a4UnauthenticatedCannotCancel() throws Exception {
        mockMvc.perform(post("/order/{orderNo}/cancel", "O-x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void a5RepeatCancelRestoresStockOnlyOnce() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));

        mockMvc.perform(post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        mockMvc.perform(post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void b1TimeoutConsumerCancelsPendingOrder() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        long orderId = queryLong("SELECT id FROM t_order");

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(
                objectMapper, orderCancelService, "localhost:9876", "test-timeout-group");
        consumer.handleMessage(timeoutMessage(orderId, orderNo));

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(6);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void b2TimeoutAfterPaidIsNoOp() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        long orderId = queryLong("SELECT id FROM t_order");
        pay(userId, orderNo);

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(
                objectMapper, orderCancelService, "localhost:9876", "test-timeout-group");
        consumer.handleMessage(timeoutMessage(orderId, orderNo));

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
    }

    @Test
    void b3TimeoutAfterManualCancelIsNoOp() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        long orderId = queryLong("SELECT id FROM t_order");

        orderCancelService.cancelByUser(userId, orderNo);

        OrderTimeoutConsumer consumer = new OrderTimeoutConsumer(
                objectMapper, orderCancelService, "localhost:9876", "test-timeout-group");
        consumer.handleMessage(timeoutMessage(orderId, orderNo));

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void b4RepeatTimeoutMessageRestoresStockOnlyOnce() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        long orderId = queryLong("SELECT id FROM t_order");

        orderCancelService.cancelByTimeout(orderId);
        orderCancelService.cancelByTimeout(orderId);

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void timeoutMissingOrderIsNoOp() {
        assertThat(orderCancelService.cancelByTimeout(999999L)).isNull();
    }

    @Test
    void multipleOrdersCancelRestoreAccurately() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProductWithSpecs(farmerId, "20.00", 5, "A", "B");
        long addressId = createAddress(userId);
        long cart1 = addCart(userId, productId, 2, "[{\"name\":\"规格\",\"value\":\"A\"}]");
        String order1 = createOrderViaApi(userId, addressId, List.of(cart1));
        long cart2 = addCart(userId, productId, 3, "[{\"name\":\"规格\",\"value\":\"B\"}]");
        String order2 = createOrderViaApi(userId, addressId, List.of(cart2));
        assertThat(productMapper.selectById(productId).getStock()).isZero();

        mockMvc.perform(post("/order/{orderNo}/cancel", order1)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/order/{orderNo}/cancel", order2)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk());

        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(5);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("5");

        // 重复取消不重复恢复
        mockMvc.perform(post("/order/{orderNo}/cancel", order1)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/order/{orderNo}/cancel", order2)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk());
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(5);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("5");
    }

    private MessageExt timeoutMessage(long orderId, String orderNo) {
        MessageExt message = new MessageExt();
        message.setTopic(MqTopics.ORDER_TIMEOUT);
        message.setTags(MqTags.ORDER_CREATED);
        message.setKeys(orderNo);
        message.setBody(("{\"orderId\":" + orderId + ",\"orderNo\":\"" + orderNo + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private String createOrderViaApi(long userId, long addressId, List<Long> cartIds) throws Exception {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(cartIds);
        MvcResult result = mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderNo").asString();
    }

    private void pay(long userId, String orderNo) {
        PayOrderDTO dto = new PayOrderDTO();
        dto.setOrderNo(orderNo);
        paymentService.pay(userId, dto, "tok-" + UUID.randomUUID());
    }

    private long createFarmer() {
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

    private long createProduct(long farmerId, String price, int stock) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("海南芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle("海南芒果 " + n);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setUnit("斤");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long createAddress(long userId) {
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13812345678");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园1栋");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    private long addCart(long userId, long productId, int quantity) {
        return addCart(userId, productId, quantity, "");
    }

    private long addCart(long userId, long productId, int quantity, String specSnapshot) {
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSpecSnapshot(specSnapshot);
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        return cart.getId();
    }

    private long createProductWithSpecs(long farmerId, String price, int stock, String specA, String specB) {
        long productId = createProduct(farmerId, price, stock);
        for (String spec : List.of(specA, specB)) {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(productId);
            attribute.setAttrName("规格");
            attribute.setAttrValue(spec);
            attribute.setExtraPrice(BigDecimal.ZERO);
            productAttributeMapper.insert(attribute);
        }
        return productId;
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
