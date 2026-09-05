package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
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

/**
 * 下单失败场景：任何失败都不能留下半个订单 / 半个库存扣减 / 被误删的购物车。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderFailureIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void emptyCartIdsRejected() throws Exception {
        long userId = 900001L;
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(1L);
        dto.setSelectedCartIds(List.of());
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void cartNotFoundFails() throws Exception {
        long userId = 900001L;
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(1L);
        dto.setSelectedCartIds(List.of(999999L));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30023));
    }

    @Test
    void otherUsersCartRejected() throws Exception {
        long owner = 900001L;
        long attacker = 900002L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        long cartId = addCart(owner, productId, 1, "");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(1L);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(attacker))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30024));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void productNotFoundFails() throws Exception {
        long userId = 900001L;
        long addressId = createAddress(userId);
        long cartId = addCart(userId, 999999L, 1, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30016));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void offShelfProductRejected() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        Product product = productMapper.selectById(productId);
        product.setLifecycle(4);
        productMapper.updateById(product);

        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30030));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void unapprovedProductRejected() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        Product product = productMapper.selectById(productId);
        product.setAuditStatus(0);
        productMapper.updateById(product);

        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30030));
    }

    @Test
    void addressNotFoundFails() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        long cartId = addCart(userId, productId, 1, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(999999L);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30006));
    }

    @Test
    void otherUsersAddressRejected() throws Exception {
        long userId = 900001L;
        long otherUser = 900002L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        long cartId = addCart(userId, productId, 1, "");
        long otherAddressId = createAddress(otherUser);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(otherAddressId);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(21002));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void invalidSpecRejected() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of("5斤装", BigDecimal.ZERO));
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 1, "[{\"name\":\"规格\",\"value\":\"99斤装\"}]");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
    }

    @Test
    void stockInsufficientFails() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 1, Map.of());
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30031));

        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(1);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 0")).isEqualTo(1);
    }

    @Test
    void batchInsufficientIsAtomic() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productA = createProduct(farmerId, "20.00", 100, Map.of());
        long productB = createProduct(farmerId, "20.00", 1, Map.of());
        long addressId = createAddress(userId);
        long cartA = addCart(userId, productA, 1, "");
        long cartB = addCart(userId, productB, 2, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartA, cartB));

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30031));

        // B 不足 → 整批失败：A 的 Redis 库存也必须原样（未被扣减）
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productA)).isEqualTo("100");
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productB)).isEqualTo("1");
        assertThat(productMapper.selectById(productA).getStock()).isEqualTo(100);
        assertThat(productMapper.selectById(productB).getStock()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 0")).isEqualTo(2);
    }

    @Test
    void mysqlDeductFailureRollsBackAndCompensatesRedis() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        // MySQL 真实库存 1，但 Redis 被错误预置为 100（模拟漂移）
        long productId = createProduct(farmerId, "20.00", 1, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "100");
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30031));

        // 事务整体回滚：无订单、无子订单、无明细、无支付，购物车保留
        assertThat(count("SELECT COUNT(*) FROM t_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_sub_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_order_item")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_payment")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 0")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(1);
        // Redis 预扣 2 后回补：100 - 2 + 2 = 100
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("100");
    }

    @Test
    void missingIdempotencyKeyRejected() throws Exception {
        long userId = 900001L;
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(1L);
        dto.setSelectedCartIds(List.of(1L));
        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void failedThenRetryWithSameTokenSucceeds() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 2, Map.of());
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 3, "");
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        String idempotencyToken = "tok-" + UUID.randomUUID();

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", idempotencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30031));

        // 修正数量后，同一 token 允许重试（业务失败已释放幂等标记）
        ShoppingCart cart = shoppingCartMapper.selectById(cartId);
        cart.setQuantity(2);
        shoppingCartMapper.updateById(cart);

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", idempotencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(40.00));

        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isZero();
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

    private long createProduct(long farmerId, String price, int stock, Map<String, BigDecimal> attrValues) {
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

        attrValues.forEach((value, extraPrice) -> {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(product.getId());
            attribute.setAttrName("规格");
            attribute.setAttrValue(value);
            attribute.setExtraPrice(extraPrice);
            productAttributeMapper.insert(attribute);
        });
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
}
