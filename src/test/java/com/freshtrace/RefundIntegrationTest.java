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
import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.trade.dto.RefundArbitrateDTO;
import com.freshtrace.trade.dto.RefundHandleDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.mapper.UserMapper;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class RefundIntegrationTest {

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
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void c1RefundPendingShipOrderRestoresStockAndClosesOrder() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("商品不符合预期"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").isNotEmpty())
                .andExpect(jsonPath("$.data.subOrderNo").value(subOrderNo))
                .andExpect(jsonPath("$.data.amount").value(40.00))
                .andExpect(jsonPath("$.data.status").value(4));

        assertThat(count("SELECT COUNT(*) FROM t_refund")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_refund")).isEqualTo(4);
        assertThat(queryString("SELECT refund_no FROM t_refund")).startsWith("R");
        assertThat(queryBigDecimal("SELECT amount FROM t_refund")).isEqualByComparingTo("40.00");
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(5);
        // 全部子订单已退款 → 主订单 CANCELLED
        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        // 库存恢复：MySQL + Redis
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void c1bPartialRefundKeepsOrderPaidUntilAllClosed() throws Exception {
        long userId = 900001L;
        long farmerA = createFarmer();
        long farmerB = createFarmer();
        long productA = createProduct(farmerA, "20.00", 100);
        long productB = createProduct(farmerB, "30.00", 100);
        long addressId = createAddress(userId);
        long cartA = addCart(userId, productA, 1);
        long cartB = addCart(userId, productB, 1);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartA, cartB));
        List<String> subOrderNos = jdbcTemplate.queryForList(
                "SELECT sub_order_no FROM t_sub_order ORDER BY amount", String.class);
        pay(userId, orderNo);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNos.get(0))
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("不想要了"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4));

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(1);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNos.get(1))
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("不想要了"))))
                .andExpect(status().isOk());

        assertThat(queryInt("SELECT status FROM t_order")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_refund")).isEqualTo(2);
        assertThat(productMapper.selectById(productA).getStock()).isEqualTo(100);
        assertThat(productMapper.selectById(productB).getStock()).isEqualTo(100);
    }

    @Test
    void c2RefundUnpaidOrderFails() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("不想要了"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30036));

        assertThat(count("SELECT COUNT(*) FROM t_refund")).isZero();
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
    }

    @Test
    void c3RefundShippedOrderCreatesAfterSaleTicketAndKeepsStock() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);
        // 模拟果农已发货：子订单进入待收货
        jdbcTemplate.update("UPDATE t_sub_order SET status = 2 WHERE sub_order_no = ?", subOrderNo);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("商品不符合预期"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value(0));

        // 创建售后工单（待处理），子订单转退款中；货已发出，不恢复库存
        assertThat(count("SELECT COUNT(*) FROM t_refund")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_refund")).isZero();
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(4);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
    }

    @Test
    void c4OtherUserCannotRefund() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(900002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("不想要了"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30026));

        assertThat(count("SELECT COUNT(*) FROM t_refund")).isZero();
    }

    @Test
    void c5RepeatRefundRestoresStockOnlyOnce() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);

        MvcResult first = mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("不想要了"))))
                .andExpect(status().isOk())
                .andReturn();
        String refundNo = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("refundNo").asString();

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("重复请求"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundNo").value(refundNo))
                .andExpect(jsonPath("$.data.status").value(4));

        assertThat(count("SELECT COUNT(*) FROM t_refund")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void c6FarmerApproveAfterSaleRefunds() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long farmerUserId = farmerUserId(farmerId);
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);
        jdbcTemplate.update("UPDATE t_sub_order SET status = 2 WHERE sub_order_no = ?", subOrderNo);
        applyAfterSale(userId, subOrderNo);
        long refundId = queryLong("SELECT id FROM t_refund");

        mockMvc.perform(post("/farmer/refund/{id}/approve", refundId)
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle("同意退款"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4));

        // 退款单已退款、子订单已退款；货已发出，不恢复库存
        assertThat(queryInt("SELECT status FROM t_refund")).isEqualTo(4);
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(5);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
    }

    @Test
    void c7FarmerRejectThenAdminArbitrateRefunds() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long farmerUserId = farmerUserId(farmerId);
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);
        jdbcTemplate.update("UPDATE t_sub_order SET status = 2 WHERE sub_order_no = ?", subOrderNo);
        applyAfterSale(userId, subOrderNo);
        long refundId = queryLong("SELECT id FROM t_refund");

        // 果农拒绝 → 退款单果农拒绝(2)，子订单恢复待收货(2)
        mockMvc.perform(post("/farmer/refund/{id}/reject", refundId)
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle("商品无问题"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
        // 果农拒绝后子订单保持退款中，等待平台仲裁（不立即恢复）
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(4);

        // 平台仲裁同意 → 退款单已退款(4)，子订单已退款(5)
        long adminUserId = createAdminUser();
        RefundArbitrateDTO dto = new RefundArbitrateDTO();
        dto.setApprove(true);
        dto.setHandleReason("判定退款成立");
        mockMvc.perform(post("/admin/refund/{id}/arbitrate", refundId)
                        .header("Authorization", "Bearer " + adminToken(adminUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4));

        assertThat(queryInt("SELECT status FROM t_refund")).isEqualTo(4);
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(5);
    }

    @Test
    void c7bAdminArbitrateRejectRestoresSubOrder() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long farmerUserId = farmerUserId(farmerId);
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);
        jdbcTemplate.update("UPDATE t_sub_order SET status = 2 WHERE sub_order_no = ?", subOrderNo);
        applyAfterSale(userId, subOrderNo);
        long refundId = queryLong("SELECT id FROM t_refund");

        mockMvc.perform(post("/farmer/refund/{id}/reject", refundId)
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle("商品无问题"))))
                .andExpect(status().isOk());

        long adminUserId = createAdminUser();
        RefundArbitrateDTO dto = new RefundArbitrateDTO();
        dto.setApprove(false);
        dto.setHandleReason("证据不足，驳回");
        mockMvc.perform(post("/admin/refund/{id}/arbitrate", refundId)
                        .header("Authorization", "Bearer " + adminToken(adminUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        // 仲裁驳回：退款单已驳回(5)，子订单恢复待收货(2)
        assertThat(queryInt("SELECT status FROM t_refund")).isEqualTo(5);
        assertThat(queryInt("SELECT status FROM t_sub_order")).isEqualTo(2);

        // 管理端工单详情与列表
        mockMvc.perform(get("/admin/refund/{id}", refundId)
                        .header("Authorization", "Bearer " + adminToken(adminUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundNo").isNotEmpty())
                .andExpect(jsonPath("$.data.statusDesc").isNotEmpty());

        mockMvc.perform(get("/admin/refund/list")
                        .header("Authorization", "Bearer " + adminToken(adminUserId))
                        .param("status", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void c8FinishedOrderOutsideAfterSaleWindowFails() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        String orderNo = createOrderViaApi(userId, addressId, List.of(cartId));
        String subOrderNo = queryString("SELECT sub_order_no FROM t_sub_order");
        pay(userId, orderNo);
        // 已完成且收货时间超过 7 天
        jdbcTemplate.update("UPDATE t_sub_order SET status = 3, received_at = DATE_SUB(NOW(), INTERVAL 8 DAY) "
                + "WHERE sub_order_no = ?", subOrderNo);

        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("超过售后期"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30064));

        assertThat(count("SELECT COUNT(*) FROM t_refund")).isZero();
    }

    private void applyAfterSale(long userId, String subOrderNo) throws Exception {
        mockMvc.perform(post("/refund/{subOrderNo}", subOrderNo)
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refund("售后申请"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    private RefundHandleDTO handle(String reason) {
        RefundHandleDTO dto = new RefundHandleDTO();
        dto.setHandleReason(reason);
        return dto;
    }

    private long farmerUserId(long farmerId) {
        Long id = jdbcTemplate.queryForObject("SELECT user_id FROM t_farmer WHERE id = ?", Long.class, farmerId);
        return id == null ? 0 : id;
    }

    private long createAdminUser() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("admin_" + n);
        user.setPasswordHash("x");
        user.setPhone("139" + String.format("%08d", n % 100000000));
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String adminToken(long userId) {
        return jwtUtils.generateAccessToken(userId, 1);
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

    private RefundApplyDTO refund(String reason) {
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setReason(reason);
        return dto;
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

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private BigDecimal queryBigDecimal(String sql) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }
}
