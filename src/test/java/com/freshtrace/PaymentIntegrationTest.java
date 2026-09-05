package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
class PaymentIntegrationTest {

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
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void paySuccessFullTransition() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrder(userId, addressId, List.of(cartId));

        // 支付前状态
        assertThat(queryInt("SELECT status FROM t_order")).isZero();
        assertThat(queryInt("SELECT status FROM t_payment")).isZero();
        assertThat(queryInt("SELECT status FROM t_sub_order")).isZero();

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.payAmount").value(40.00))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT paid_at FROM t_order", java.sql.Timestamp.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT paid_at FROM t_payment", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void payMovesAllSubOrdersToPendingShip() throws Exception {
        long userId = 900001L;
        long farmerA = createFarmer();
        long farmerB = createFarmer();
        long productA = createProduct(farmerA, "20.00", 100);
        long productB = createProduct(farmerB, "30.00", 100);
        long addressId = createAddress(userId);
        long cartA = addCart(userId, productA, 1);
        long cartB = addCart(userId, productB, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartA, cartB));

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payAmount").value(50.00));

        assertThat(count("SELECT COUNT(*) FROM t_sub_order WHERE status = 1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM t_sub_order WHERE status = 0")).isZero();
    }

    @Test
    void payOrderNotFound() throws Exception {
        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(900001L))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay("O-not-exist"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30025));
    }

    @Test
    void payOthersOrderRejected() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartId));

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(900002L))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30026));

        assertThat(queryInt("SELECT status FROM t_order")).isZero();
        assertThat(queryInt("SELECT status FROM t_payment")).isZero();
    }

    @Test
    void payCancelledOrderRejected() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartId));
        jdbcTemplate.update("UPDATE t_order SET status = 5, cancelled_at = NOW() WHERE order_no = ?", orderNo);

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30027));

        assertThat(queryInt("SELECT status FROM t_payment")).isZero();
    }

    @Test
    void duplicatePaySameTokenReturnsFirstResult() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartId));
        String idempotencyToken = "tok-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", idempotencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn();
        String paidAt1 = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("paidAt").asString();

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", idempotencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.paidAt").value(paidAt1));

        assertThat(count("SELECT COUNT(*) FROM t_payment")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(1);
    }

    @Test
    void duplicatePayDifferentTokenReturnsFirstResult() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartId));

        MvcResult first = mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andReturn();
        String paidAt1 = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("paidAt").asString();

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAt").value(paidAt1));

        assertThat(count("SELECT COUNT(*) FROM t_payment")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_payment")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(1);
    }

    @Test
    void payMissingIdempotencyKeyRejected() throws Exception {
        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(900001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay("O-x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void payBlankOrderNoRejected() throws Exception {
        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(900001L))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(post("/payment/pay")
                        .header("Idempotency-Key", "tok-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay("O-x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subOrderStatusMismatchRollsBackWholePayment() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1);
        String orderNo = createOrder(userId, addressId, List.of(cartId));

        // 人为制造不一致：子订单被置为已取消（模拟脏数据）
        jdbcTemplate.update("UPDATE t_sub_order SET status = 6 WHERE order_id = (SELECT id FROM t_order WHERE order_no = ?)", orderNo);

        mockMvc.perform(post("/payment/pay")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(pay(orderNo))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));

        // 整体回滚：Payment/Order 均回到支付前状态
        assertThat(queryInt("SELECT status FROM t_payment")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT paid_at FROM t_payment", java.sql.Timestamp.class)).isNull();
        assertThat(queryInt("SELECT status FROM t_order")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT paid_at FROM t_order", java.sql.Timestamp.class)).isNull();
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
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSpecSnapshot("");
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        return cart.getId();
    }

    private String createOrder(long userId, long addressId, List<Long> cartIds) throws Exception {
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

    private PayOrderDTO pay(String orderNo) {
        PayOrderDTO dto = new PayOrderDTO();
        dto.setOrderNo(orderNo);
        return dto;
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
