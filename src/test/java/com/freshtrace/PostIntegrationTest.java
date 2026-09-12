package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.community.dto.PostCreateDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动态社区端到端集成测试（Phase 6 Day 5）：
 * 发帖 → 时间线 → 详情 → 嵌套评论 → 点赞/取消 → 果农主页聚合。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PostIntegrationTest {

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
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void publishPostAppearsInTimelineAndDetail() throws Exception {
        FarmerRef farmer = createFarmer();
        long productId = createOnSaleProduct(farmer.farmerId(), "海南芒果");

        long postId = createPost(farmer.userId(), "今天果园天气很好",
                productId, null, List.of("http://img/p1.png", "http://img/p2.png"));

        JsonNode timeline = getJson("/post/page?farmerId=" + farmer.farmerId(), farmer.userId()).path("data");
        assertThat(timeline.path("total").asLong()).isEqualTo(1);
        JsonNode first = timeline.path("records").get(0);
        assertThat(first.path("id").asLong()).isEqualTo(postId);
        assertThat(first.path("content").asText()).isEqualTo("今天果园天气很好");
        assertThat(first.path("orchardName").asText()).isEqualTo("测试果园");
        assertThat(first.path("images").size()).isEqualTo(2);
        assertThat(first.path("likeCount").asInt()).isZero();
        assertThat(first.path("commentCount").asInt()).isZero();

        JsonNode detail = getJson("/post/" + postId, farmer.userId()).path("data");
        assertThat(detail.path("images").get(0).asText()).isEqualTo("http://img/p1.png");
        assertThat(detail.path("productId").asLong()).isEqualTo(productId);
        assertThat(detail.path("comments").size()).isZero();
    }

    @Test
    void nestedCommentTreeAssembled() throws Exception {
        FarmerRef farmer = createFarmer();
        long postId = createPost(farmer.userId(), "欢迎下单", null, null, List.of());

        long topCommentId = createComment(900001L, postId, null, "看起来不错");
        createComment(900002L, postId, topCommentId, "我也觉得");

        JsonNode detail = getJson("/post/" + postId, 900001L).path("data");
        JsonNode comments = detail.path("comments");
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments.get(0).path("id").asLong()).isEqualTo(topCommentId);
        assertThat(comments.get(0).path("children").size()).isEqualTo(1);
        assertThat(comments.get(0).path("children").get(0).path("content").asText()).isEqualTo("我也觉得");
        assertThat(detail.path("commentCount").asInt()).isEqualTo(2);
    }

    @Test
    void replyToCommentOfAnotherPostRejected() throws Exception {
        FarmerRef farmer = createFarmer();
        long postA = createPost(farmer.userId(), "A", null, null, List.of());
        long postB = createPost(farmer.userId(), "B", null, null, List.of());
        long commentOfA = createComment(900001L, postA, null, "对A的评论");

        mockMvc.perform(post("/post/" + postB + "/comment")
                        .header("Authorization", "Bearer " + token(900002L))
                        .contentType("application/json")
                        .content("{\"parentId\":" + commentOfA + ",\"content\":\"错位回复\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30045));
    }

    @Test
    void likeThenUnlikeUpdatesCount() throws Exception {
        FarmerRef farmer = createFarmer();
        long postId = createPost(farmer.userId(), "点赞测试", null, null, List.of());
        long likerId = 900003L;

        mockMvc.perform(post("/post/" + postId + "/like")
                        .header("Authorization", "Bearer " + token(likerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        JsonNode liked = getJson("/post/" + postId, likerId).path("data");
        assertThat(liked.path("likeCount").asInt()).isEqualTo(1);
        assertThat(liked.path("liked").asBoolean()).isTrue();

        mockMvc.perform(delete("/post/" + postId + "/like")
                        .header("Authorization", "Bearer " + token(likerId)))
                .andExpect(status().isOk());

        JsonNode unliked = getJson("/post/" + postId, likerId).path("data");
        assertThat(unliked.path("likeCount").asInt()).isZero();
        assertThat(unliked.path("liked").asBoolean()).isFalse();
    }

    @Test
    void duplicateLikeIsIdempotent() throws Exception {
        FarmerRef farmer = createFarmer();
        long postId = createPost(farmer.userId(), "重复点赞", null, null, List.of());
        long likerId = 900004L;

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/post/" + postId + "/like")
                            .header("Authorization", "Bearer " + token(likerId)))
                    .andExpect(status().isOk());
        }

        JsonNode detail = getJson("/post/" + postId, likerId).path("data");
        assertThat(detail.path("likeCount").asInt()).isEqualTo(1);
        assertThat(detail.path("liked").asBoolean()).isTrue();
    }

    @Test
    void unlikeWithoutLikeIsNoop() throws Exception {
        FarmerRef farmer = createFarmer();
        long postId = createPost(farmer.userId(), "未点赞取消", null, null, List.of());

        mockMvc.perform(delete("/post/" + postId + "/like")
                        .header("Authorization", "Bearer " + token(900005L)))
                .andExpect(status().isOk());

        JsonNode detail = getJson("/post/" + postId, 900005L).path("data");
        assertThat(detail.path("likeCount").asInt()).isZero();
    }

    @Test
    void farmerHomeAggregatesProfile() throws Exception {
        FarmerRef farmer = createFarmer();
        createOnSaleProduct(farmer.farmerId(), "在售芒果");
        createPost(farmer.userId(), "主页动态", null, null, List.of());

        JsonNode home = getJson("/farmer/" + farmer.farmerId() + "/home", farmer.userId()).path("data");
        assertThat(home.path("orchardName").asText()).isEqualTo("测试果园");
        assertThat(home.path("avgRating").asDouble()).isEqualTo(5.0);
        assertThat(home.path("reviewCount").asInt()).isZero();
        assertThat(home.path("products").size()).isEqualTo(1);
        assertThat(home.path("posts").size()).isEqualTo(1);
        assertThat(home.path("posts").get(0).path("content").asText()).isEqualTo("主页动态");
    }

    @Test
    void farmerHomeCacheWrittenThenEvictedOnPost() throws Exception {
        FarmerRef farmer = createFarmer();
        String cacheKey = CacheKeys.farmerHome(farmer.farmerId());

        getJson("/farmer/" + farmer.farmerId() + "/home", farmer.userId());
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();

        createPost(farmer.userId(), "新动态触发缓存失效", null, null, List.of());
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();
    }

    @Test
    void rejectLinkingOtherFarmerProduct() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productOfB = createOnSaleProduct(farmerB.farmerId(), "B的商品");

        PostCreateDTO dto = new PostCreateDTO();
        dto.setContent("借用他人商品");
        dto.setProductId(productOfB);
        mockMvc.perform(post("/post")
                        .header("Authorization", "Bearer " + token(farmerA.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));
    }

    @Test
    void deleteByNonOwnerRejected() throws Exception {
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long postId = createPost(farmerA.userId(), "A的动态", null, null, List.of());

        mockMvc.perform(delete("/post/" + postId)
                        .header("Authorization", "Bearer " + token(farmerB.userId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30044));
    }

    @Test
    void createPostRequiresLogin() throws Exception {
        mockMvc.perform(post("/post")
                        .contentType("application/json")
                        .content("{\"content\":\"匿名发帖\"}"))
                .andExpect(status().isUnauthorized());
    }

    private long createPost(long farmerUserId, String content, Long productId, Long traceNodeId,
                            List<String> images) throws Exception {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setContent(content);
        dto.setProductId(productId);
        dto.setTraceNodeId(traceNodeId);
        dto.setImages(images);
        MvcResult result = mockMvc.perform(post("/post")
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createComment(long userId, long postId, Long parentId, String content) throws Exception {
        String body = parentId == null
                ? "{\"content\":\"" + content + "\"}"
                : "{\"parentId\":" + parentId + ",\"content\":\"" + content + "\"}";
        MvcResult result = mockMvc.perform(post("/post/" + postId + "/comment")
                        .header("Authorization", "Bearer " + token(userId))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
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

    private long createOnSaleProduct(long farmerId, String title) {
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
