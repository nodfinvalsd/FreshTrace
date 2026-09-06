package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trace.dto.TraceNodeCreateDTO;
import com.freshtrace.trace.dto.TraceNodeUpdateDTO;
import com.freshtrace.trace.entity.TraceNode;
import com.freshtrace.trace.mapper.TraceNodeMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 溯源系统集成测试（Phase 5 Day 4）。
 * 覆盖核心业务规则：商品归属校验、Timeline 排序/隔离、逻辑删除、公开查询、模板不写库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TraceIntegrationTest {

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
    private TraceNodeMapper traceNodeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    // ============ 归属校验 ============

    @Test
    void createNodeOnOwnProductSucceeds() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");

        mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(createBody(productId, 1, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        assertThat(traceNodeMapper.selectCount(new LambdaQueryWrapper<TraceNode>()
                .eq(TraceNode::getProductId, productId))).isEqualTo(1);
    }

    @Test
    void createNodeOnOtherFarmerProductRejected() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productB = createProduct(farmerB.farmerId(), "芒果B");

        mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmerA.userId()))
                        .contentType("application/json")
                        .content(createBody(productB, 1, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));

        assertThat(traceNodeMapper.selectCount(new LambdaQueryWrapper<TraceNode>()
                .eq(TraceNode::getProductId, productB))).isZero();
    }

    @Test
    void updateOtherFarmerNodeRejected() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productB = createProduct(farmerB.farmerId(), "芒果B");
        long nodeId = createNodeViaHttp(farmerB.userId(), productB, 1, "播种", LocalDate.of(2026, 9, 1));

        mockMvc.perform(put("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(farmerA.userId()))
                        .contentType("application/json")
                        .content(updateBody(2, "施肥", LocalDate.of(2026, 9, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));

        TraceNode node = traceNodeMapper.selectById(nodeId);
        assertThat(node.getTitle()).isEqualTo("播种");
        assertThat(node.getNodeType()).isEqualTo(1);
    }

    @Test
    void deleteOtherFarmerNodeRejected() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productB = createProduct(farmerB.farmerId(), "芒果B");
        long nodeId = createNodeViaHttp(farmerB.userId(), productB, 1, "播种", LocalDate.of(2026, 9, 1));

        mockMvc.perform(delete("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(farmerA.userId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_trace_node WHERE id = ?", Integer.class, nodeId);
        assertThat(deleted).isZero();
    }

    @Test
    void updateNotFound() throws Exception {
        FarmerRef farmer = createFarmer();
        mockMvc.perform(put("/trace/nodes/{id}", 99999999L)
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(updateBody(1, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30042));
    }

    @Test
    void deleteNotFound() throws Exception {
        FarmerRef farmer = createFarmer();
        mockMvc.perform(delete("/trace/nodes/{id}", 99999999L)
                        .header("Authorization", "Bearer " + token(farmer.userId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30042));
    }

    @Test
    void createNodeProductNotFound() throws Exception {
        FarmerRef farmer = createFarmer();
        mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(createBody(99999999L, 1, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30016));
    }

    @Test
    void createNodeInvalidTypeRejected() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(createBody(productId, 99, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    // ============ Timeline ============

    @Test
    void timelineSortedByOccurredAtAsc() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        createNodeViaHttp(farmer.userId(), productId, 3, "开花", LocalDate.of(2026, 9, 3));
        createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));
        createNodeViaHttp(farmer.userId(), productId, 2, "施肥", LocalDate.of(2026, 9, 2));

        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.size()).isEqualTo(3);
        assertThat(data.get(0).path("occurredAt").asText()).isEqualTo("2026-09-01");
        assertThat(data.get(1).path("occurredAt").asText()).isEqualTo("2026-09-02");
        assertThat(data.get(2).path("occurredAt").asText()).isEqualTo("2026-09-03");
    }

    @Test
    void timelineSameDaySortedByIdAsc() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        long id1 = createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));
        long id2 = createNodeViaHttp(farmer.userId(), productId, 2, "施肥", LocalDate.of(2026, 9, 1));
        long id3 = createNodeViaHttp(farmer.userId(), productId, 3, "开花", LocalDate.of(2026, 9, 1));

        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.size()).isEqualTo(3);
        assertThat(data.get(0).path("id").asLong()).isEqualTo(id1);
        assertThat(data.get(1).path("id").asLong()).isEqualTo(id2);
        assertThat(data.get(2).path("id").asLong()).isEqualTo(id3);
    }

    @Test
    void timelineOnlyReturnsSpecifiedProduct() throws Exception {
        FarmerRef farmer = createFarmer();
        long productA = createProduct(farmer.farmerId(), "芒果A");
        long productB = createProduct(farmer.farmerId(), "芒果B");
        createNodeViaHttp(farmer.userId(), productA, 1, "播种A", LocalDate.of(2026, 9, 1));
        createNodeViaHttp(farmer.userId(), productA, 2, "施肥A", LocalDate.of(2026, 9, 2));
        createNodeViaHttp(farmer.userId(), productB, 1, "播种B", LocalDate.of(2026, 9, 1));

        JsonNode data = getTimeline(productA, farmer.userId());
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).path("productId").asLong()).isEqualTo(productA);
        assertThat(data.get(1).path("productId").asLong()).isEqualTo(productA);
    }

    @Test
    void timelineAccessibleToNormalUser() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));

        // 普通用户（非果农）也能查看溯源时间线
        long normalUserId = 900001L;
        JsonNode data = getTimeline(productId, normalUserId);
        assertThat(data.size()).isEqualTo(1);
    }

    @Test
    void timelineUnauthenticatedRejected() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));

        mockMvc.perform(get("/trace/timeline/{productId}", productId))
                .andExpect(status().isUnauthorized());
    }

    // ============ 逻辑删除 ============

    @Test
    void deleteIsLogicalDelete() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        long nodeId = createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));

        mockMvc.perform(delete("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(farmer.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 行仍存在，deleted=1（逻辑删除而非物理删除）
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_trace_node WHERE id = ?", Integer.class, nodeId);
        assertThat(deleted).isEqualTo(1);

        // 逻辑删除节点不再出现在 Timeline
        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.size()).isZero();
    }

    // ============ 权限 ============

    @Test
    void normalUserCannotWriteTraceNodes() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        long nodeId = createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));
        long normalUserId = 900001L;

        mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(normalUserId))
                        .contentType("application/json")
                        .content(createBody(productId, 1, "播种", LocalDate.of(2026, 9, 1))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(normalUserId))
                        .contentType("application/json")
                        .content(updateBody(2, "施肥", LocalDate.of(2026, 9, 2))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(normalUserId)))
                .andExpect(status().isForbidden());
    }

    // ============ 图片 / 日期 ============

    @Test
    void imagesJsonRoundTrip() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        TraceNodeCreateDTO dto = new TraceNodeCreateDTO();
        dto.setProductId(productId);
        dto.setNodeType(1);
        dto.setTitle("播种");
        dto.setImages(List.of("https://example.com/a.jpg", "https://example.com/b.jpg"));
        dto.setOccurredAt(LocalDate.of(2026, 9, 1));

        MvcResult result = mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andReturn();

        long nodeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        TraceNode node = traceNodeMapper.selectById(nodeId);
        assertThat(node.getImages()).contains("https://example.com/a.jpg");

        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.get(0).path("images").size()).isEqualTo(2);
        assertThat(data.get(0).path("images").get(0).asText()).isEqualTo("https://example.com/a.jpg");
        assertThat(data.get(0).path("images").get(1).asText()).isEqualTo("https://example.com/b.jpg");
    }

    @Test
    void occurredAtDatePreserved() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");
        long nodeId = createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));

        TraceNode node = traceNodeMapper.selectById(nodeId);
        assertThat(node.getOccurredAt()).isEqualTo(LocalDate.of(2026, 9, 1));

        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.get(0).path("occurredAt").asText()).isEqualTo("2026-09-01");
    }

    // ============ 完整闭环 ============

    @Test
    void fullLifecycleCreateViewDelete() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");

        long nodeId = createNodeViaHttp(farmer.userId(), productId, 1, "播种", LocalDate.of(2026, 9, 1));

        JsonNode data = getTimeline(productId, farmer.userId());
        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).path("id").asLong()).isEqualTo(nodeId);

        mockMvc.perform(delete("/trace/nodes/{id}", nodeId)
                        .header("Authorization", "Bearer " + token(farmer.userId())))
                .andExpect(status().isOk());

        JsonNode afterDelete = getTimeline(productId, farmer.userId());
        assertThat(afterDelete.size()).isZero();
    }

    // ============ 模板 ============

    @Test
    void templateQueryDoesNotCreateTraceNode() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "海南芒果");

        long before = traceNodeMapper.selectCount(null);
        mockMvc.perform(get("/trace/templates/{categoryId}", 1L)
                        .header("Authorization", "Bearer " + token(farmer.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(7));
        long after = traceNodeMapper.selectCount(null);

        assertThat(after).isEqualTo(before);
    }

    // ============ helpers ============

    private long createNodeViaHttp(long farmerUserId, long productId, int nodeType,
                                   String title, LocalDate occurredAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/trace/nodes")
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType("application/json")
                        .content(createBody(productId, nodeType, title, occurredAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String createBody(long productId, int nodeType, String title, LocalDate occurredAt) throws Exception {
        TraceNodeCreateDTO dto = new TraceNodeCreateDTO();
        dto.setProductId(productId);
        dto.setNodeType(nodeType);
        dto.setTitle(title);
        dto.setOccurredAt(occurredAt);
        return objectMapper.writeValueAsString(dto);
    }

    private String updateBody(int nodeType, String title, LocalDate occurredAt) throws Exception {
        TraceNodeUpdateDTO dto = new TraceNodeUpdateDTO();
        dto.setNodeType(nodeType);
        dto.setTitle(title);
        dto.setOccurredAt(occurredAt);
        return objectMapper.writeValueAsString(dto);
    }

    private JsonNode getTimeline(long productId, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/trace/timeline/{productId}", productId)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private FarmerRef createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("trace_farmer_" + n);
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

    private long createProduct(long farmerId, String title) {
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
