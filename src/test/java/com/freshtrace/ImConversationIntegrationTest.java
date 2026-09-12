package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话端到端集成测试（Phase 8 Day 2）：
 * 创建幂等 + 来源商品校验 + 买家/果农双视角列表。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ImConversationIntegrationTest {

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
    private ConversationMapper conversationMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void createIsIdempotentAndCarriesPeerProductInfo() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId());

        JsonNode first = createConversation(buyerId, farmer.farmerId(), productId).path("data");
        assertThat(first.path("id").asLong()).isPositive();
        assertThat(first.path("peerRole").asInt()).isEqualTo(1);
        assertThat(first.path("peerName").asText()).isEqualTo("测试果园");
        assertThat(first.path("productId").asLong()).isEqualTo(productId);
        assertThat(first.path("productTitle").asText()).startsWith("芒果");

        JsonNode second = createConversation(buyerId, farmer.farmerId(), productId).path("data");
        assertThat(second.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(conversationMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void createWithoutProductSucceeds() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();

        JsonNode data = createConversation(buyerId, farmer.farmerId(), null).path("data");
        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("productId").isNull()).isTrue();
    }

    @Test
    void buyerListShowsFarmerPeer() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        createConversation(buyerId, farmer.farmerId(), null);

        JsonNode list = getJson("/im/conversations?page=1&size=10", buyerId).path("data");
        assertThat(list.path("total").asLong()).isEqualTo(1);
        JsonNode record = list.path("records").get(0);
        assertThat(record.path("peerRole").asInt()).isEqualTo(1);
        assertThat(record.path("peerName").asText()).isEqualTo("测试果园");
        assertThat(record.path("unreadCount").asInt()).isZero();
    }

    @Test
    void farmerListShowsBuyerPeer() throws Exception {
        long buyerId = createBuyer("小李");
        FarmerRef farmer = createFarmer();
        createConversation(buyerId, farmer.farmerId(), null);

        JsonNode list = getJson("/im/conversations?page=1&size=10", farmer.userId()).path("data");
        assertThat(list.path("total").asLong()).isEqualTo(1);
        JsonNode record = list.path("records").get(0);
        assertThat(record.path("peerRole").asInt()).isZero();
        assertThat(record.path("peerName").asText()).isEqualTo("小李");
    }

    @Test
    void createRejectsSelfConversation() throws Exception {
        FarmerRef farmer = createFarmer();

        mockMvc.perform(post("/im/conversations")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("farmerId", farmer.farmerId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30059));
    }

    @Test
    void createRejectsProductOfAnotherFarmer() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productOfB = createProduct(farmerB.farmerId());

        mockMvc.perform(post("/im/conversations")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("farmerId", farmerA.farmerId(), "productId", productOfB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30060));
    }

    @Test
    void createRejectsInvalidFarmer() throws Exception {
        long buyerId = createBuyer();

        mockMvc.perform(post("/im/conversations")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("farmerId", 999999L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30058));
    }

    @Test
    void imEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/im/conversations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/im/unread-count"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/im/conversations/1/read"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createConversation(long userId, long farmerId, Long productId) throws Exception {
        Map<String, Object> body = productId == null
                ? Map.of("farmerId", farmerId)
                : Map.of("farmerId", farmerId, "productId", productId);
        MvcResult result = mockMvc.perform(post("/im/conversations")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createBuyer() {
        return createBuyer(null);
    }

    private long createBuyer(String nickname) {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("buyer_" + n);
        user.setPasswordHash("x");
        user.setNickname(nickname == null ? "买家" + n : nickname);
        user.setPhone("138" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private FarmerRef createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
        user.setNickname("果农" + n);
        user.setPhone("137" + String.format("%08d", n % 100000000));
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

    private long createProduct(long farmerId) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle("芒果" + n);
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(100);
        product.setUnit("斤");
        product.setMainImage("http://img/x.png");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }
}
