package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.presale.dto.PresaleCreateDTO;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
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
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预售端到端集成测试（Phase 7 Day 2）：
 * 果农设置/关闭预售 → 商品生命周期联动 → 用户预约 → 重复/满额拦截 → 我的预约/列表/详情。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PresaleIntegrationTest {

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
    private PresaleMapper presaleMapper;

    @Autowired
    private PresaleReservationMapper presaleReservationMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void createPresaleMovesProductToPresaleAndAppearsInList() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "预售芒果");

        long presaleId = createPresale(farmer.userId(), productId, hours(-1), days(1), 5);

        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(1);

        JsonNode detail = getJson("/presale/" + presaleId, farmer.userId()).path("data");
        assertThat(detail.path("productId").asLong()).isEqualTo(productId);
        assertThat(detail.path("status").asInt()).isEqualTo(1);
        assertThat(detail.path("statusDesc").asText()).isEqualTo("进行中");
        assertThat(detail.path("productLifecycle").asInt()).isEqualTo(1);

        JsonNode list = getJson("/presale/list?page=1&size=10", farmer.userId()).path("data");
        assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("records").get(0).path("id").asLong()).isEqualTo(presaleId);
    }

    @Test
    void reserveThenDuplicateRejectedAndMyReservations() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "预约芒果");
        long presaleId = createPresale(farmer.userId(), productId, hours(-1), days(1), 5);
        long buyerId = 900101L;

        reserve(buyerId, presaleId, 1);

        JsonNode mine = getJson("/presale/my-reservations?page=1&size=10", buyerId).path("data");
        assertThat(mine.path("total").asLong()).isEqualTo(1);
        assertThat(mine.path("records").get(0).path("productId").asLong()).isEqualTo(productId);
        assertThat(mine.path("records").get(0).path("presaleStatus").asInt()).isEqualTo(1);
        assertThat(mine.path("records").get(0).path("notified").asInt()).isZero();

        mockMvc.perform(post("/presale/" + presaleId + "/reserve")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30052));

        assertThat(presaleReservationMapper.selectCount(null)).isEqualTo(1);
        assertThat(presaleMapper.selectById(presaleId).getReservationCount()).isEqualTo(1);
    }

    @Test
    void maxReservationsEnforced() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "限购预售");
        long presaleId = createPresale(farmer.userId(), productId, hours(-1), days(1), 1);

        reserve(900201L, presaleId, 1);

        mockMvc.perform(post("/presale/" + presaleId + "/reserve")
                        .header("Authorization", "Bearer " + token(900202L))
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30053));

        assertThat(presaleReservationMapper.selectCount(null)).isEqualTo(1);
        assertThat(presaleMapper.selectById(presaleId).getReservationCount()).isEqualTo(1);
    }

    @Test
    void reserveBeforeStartRejected() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "未开始预售");
        long presaleId = createPresale(farmer.userId(), productId, hours(1), days(1), 5);

        mockMvc.perform(post("/presale/" + presaleId + "/reserve")
                        .header("Authorization", "Bearer " + token(900301L))
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30048));
    }

    @Test
    void closePresaleRevertsProductAndStopsReservation() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "关闭预售");
        long presaleId = createPresale(farmer.userId(), productId, hours(-1), days(1), 5);

        mockMvc.perform(post("/farmer/presale/" + presaleId + "/close")
                        .header("Authorization", "Bearer " + token(farmer.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isZero();
        assertThat(productMapper.selectById(productId).getLifecycle()).isZero();

        JsonNode list = getJson("/presale/list?page=1&size=10", farmer.userId()).path("data");
        assertThat(list.path("total").asLong()).isZero();

        mockMvc.perform(post("/presale/" + presaleId + "/reserve")
                        .header("Authorization", "Bearer " + token(900401L))
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30048));
    }

    @Test
    void createPresaleRejectsNonOwnerProduct() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productOfB = createProduct(farmerB.farmerId(), 0, 1, "B的商品");

        mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(farmerA.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                buildCreateDTO(productOfB, hours(-1), days(1), 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));
    }

    @Test
    void createPresaleRejectsUnapprovedProduct() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 0, "待审核商品");

        mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                buildCreateDTO(productId, hours(-1), days(1), 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30051));
    }

    @Test
    void createPresaleRejectsDuplicateProduct() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "重复配置");
        createPresale(farmer.userId(), productId, hours(-1), days(1), 5);

        mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                buildCreateDTO(productId, hours(-1), days(1), 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30047));
    }

    @Test
    void createPresaleRejectsInvalidTime() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "时间非法");

        mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                buildCreateDTO(productId, days(-2), days(-1), 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30050));
    }

    @Test
    void createPresaleRequiresFarmer() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), 0, 1, "非果农设置");
        long plainUserId = 900501L;

        mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(plainUserId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                buildCreateDTO(productId, hours(-1), days(1), 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(21002));
    }

    private PresaleCreateDTO buildCreateDTO(long productId, LocalDateTime start, LocalDateTime end, Integer max) {
        PresaleCreateDTO dto = new PresaleCreateDTO();
        dto.setProductId(productId);
        dto.setPresaleStart(start);
        dto.setPresaleEnd(end);
        dto.setExpectedHarvest(end);
        dto.setMaxReservations(max);
        return dto;
    }

    private long createPresale(long farmerUserId, long productId, LocalDateTime start, LocalDateTime end,
                               Integer max) throws Exception {
        MvcResult result = mockMvc.perform(post("/farmer/presale")
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(buildCreateDTO(productId, start, end, max))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void reserve(long userId, long presaleId, int quantity) throws Exception {
        mockMvc.perform(post("/presale/" + presaleId + "/reserve")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType("application/json")
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private JsonNode getJson(String url, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private FarmerRef createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
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

    private long createProduct(long farmerId, int lifecycle, int auditStatus, String title) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle(title);
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(100);
        product.setUnit("斤");
        product.setMainImage("http://img/x.png");
        product.setLifecycle(lifecycle);
        product.setAuditStatus(auditStatus);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private LocalDateTime hours(int hours) {
        return LocalDateTime.now().withNano(0).plusHours(hours);
    }

    private LocalDateTime days(int days) {
        return LocalDateTime.now().withNano(0).plusDays(days);
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }
}
