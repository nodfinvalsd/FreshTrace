package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.CategoryMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
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
class OrderCreateIntegrationTest {

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
    private CategoryMapper categoryMapper;

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

    private static final String SPEC_10 = "[{\"name\":\"规格\",\"value\":\"10斤装\"}]";

    @Test
    void createOrderFullFlow() throws Exception {
        long farmerId = createFarmer();
        long productA = createProduct(farmerId, "20.00", 100, Map.of());
        long productB = createProduct(farmerId, "20.00", 50,
                Map.of("5斤装", BigDecimal.ZERO, "10斤装", new BigDecimal("30.00")));
        long userId = 900001L;
        long addressId = createAddress(userId, "张三", "13812345678");
        long cartA = addCart(userId, productA, 2, "");
        long cartB = addCart(userId, productB, 1, SPEC_10);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartA, cartB));
        dto.setRemark("尽快发货");

        MvcResult result = mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(90.00))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.subOrders.length()").value(1))
                .andExpect(jsonPath("$.data.subOrders[0].farmerId").value(farmerId))
                .andExpect(jsonPath("$.data.subOrders[0].amount").value(90.00))
                .andReturn();

        String orderNo = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderNo").asString();
        assertThat(orderNo).startsWith("O");

        // 主订单
        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(1);
        assertThat(queryLong("SELECT user_id FROM t_order")).isEqualTo(userId);
        assertThat(queryBigDecimal("SELECT total_amount FROM t_order")).isEqualByComparingTo("90.00");
        assertThat(queryBigDecimal("SELECT pay_amount FROM t_order")).isEqualByComparingTo("90.00");
        assertThat(queryInt("SELECT status FROM t_order")).isZero();
        assertThat(queryString("SELECT remark FROM t_order")).isEqualTo("尽快发货");
        JsonNode addressSnapshot = objectMapper.readTree(queryString("SELECT address_snapshot FROM t_order"));
        assertThat(addressSnapshot.path("receiverName").asString()).isEqualTo("张三");
        assertThat(addressSnapshot.path("receiverPhone").asString()).isEqualTo("13812345678");
        assertThat(addressSnapshot.path("detail").asString()).isEqualTo("科技园1栋");

        // 子订单：同果农合并
        assertThat(count("SELECT COUNT(*) FROM t_sub_order")).isEqualTo(1);
        assertThat(queryLong("SELECT farmer_id FROM t_sub_order")).isEqualTo(farmerId);
        assertThat(queryBigDecimal("SELECT amount FROM t_sub_order")).isEqualByComparingTo("90.00");
        assertThat(queryString("SELECT sub_order_no FROM t_sub_order")).startsWith("S");

        // 订单明细快照
        assertThat(count("SELECT COUNT(*) FROM t_order_item")).isEqualTo(2);
        JsonNode items = objectMapper.readTree(queryString("SELECT spec_snapshot FROM t_order_item WHERE quantity = 1"));
        assertThat(items.get(0).path("name").asString()).isEqualTo("规格");
        assertThat(items.get(0).path("value").asString()).isEqualTo("10斤装");
        assertThat(items.get(0).path("extraPrice").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(queryBigDecimal("SELECT price FROM t_order_item WHERE quantity = 1")).isEqualByComparingTo("50.00");
        assertThat(queryBigDecimal("SELECT total_price FROM t_order_item WHERE quantity = 1")).isEqualByComparingTo("50.00");
        assertThat(queryBigDecimal("SELECT price FROM t_order_item WHERE quantity = 2")).isEqualByComparingTo("20.00");
        assertThat(queryBigDecimal("SELECT total_price FROM t_order_item WHERE quantity = 2")).isEqualByComparingTo("40.00");
        assertThat(queryString("SELECT product_title FROM t_order_item WHERE quantity = 2")).isNotBlank();

        // 支付记录
        assertThat(count("SELECT COUNT(*) FROM t_payment")).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM t_payment")).isZero();
        assertThat(queryString("SELECT pay_method FROM t_payment")).isEqualTo("SIMULATED");
        assertThat(queryString("SELECT payment_no FROM t_payment")).startsWith("P");
        assertThat(queryBigDecimal("SELECT amount FROM t_payment")).isEqualByComparingTo("90.00");

        // 购物车删除（逻辑删除）
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 1")).isEqualTo(2);

        // MySQL 最终扣减
        assertThat(productMapper.selectById(productA).getStock()).isEqualTo(98);
        assertThat(productMapper.selectById(productB).getStock()).isEqualTo(49);

        // Redis 预扣后一致
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productA)).isEqualTo("98");
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productB)).isEqualTo("49");
    }

    @Test
    void splitByFarmerCreatesTwoSubOrders() throws Exception {
        long farmerA = createFarmer();
        long farmerB = createFarmer();
        long productA = createProduct(farmerA, "20.00", 100, Map.of());
        long productB = createProduct(farmerB, "30.00", 100, Map.of());
        long userId = 900001L;
        long addressId = createAddress(userId, "张三", "13812345678");
        long cartA = addCart(userId, productA, 1, "");
        long cartB = addCart(userId, productB, 1, "");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartA, cartB));

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", "tok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(50.00))
                .andExpect(jsonPath("$.data.subOrders.length()").value(2))
                .andExpect(jsonPath("$.data.subOrders[0].amount").value(20.00))
                .andExpect(jsonPath("$.data.subOrders[1].amount").value(30.00));

        assertThat(count("SELECT COUNT(*) FROM t_sub_order")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM t_order_item")).isEqualTo(2);
        List<Long> farmerIds = jdbcTemplate.queryForList("SELECT farmer_id FROM t_sub_order ORDER BY amount", Long.class);
        assertThat(farmerIds).containsExactlyInAnyOrder(farmerA, farmerB);
    }

    @Test
    void idempotentRepeatReturnsSameOrderNo() throws Exception {
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, Map.of());
        long userId = 900001L;
        long addressId = createAddress(userId, "张三", "13812345678");
        long cartId = addCart(userId, productId, 2, "");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        String token = "tok-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String orderNo1 = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("orderNo").asString();

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token(userId))
                        .header("Idempotency-Key", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo1));

        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + productId)).isEqualTo("98");
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(1L);
        dto.setSelectedCartIds(List.of(1L));
        mockMvc.perform(post("/order")
                        .header("Idempotency-Key", "tok-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isUnauthorized());
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
        product.setMainImage("http://img/" + n + ".png");
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

    private long createAddress(long userId, String name, String phone) {
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(name);
        address.setReceiverPhone(phone);
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

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
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
