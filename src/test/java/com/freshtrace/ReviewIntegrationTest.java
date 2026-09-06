package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.review.dto.ReviewCreateDTO;
import com.freshtrace.review.dto.ReviewReplyDTO;
import com.freshtrace.review.entity.Review;
import com.freshtrace.review.mapper.ReviewMapper;
import com.freshtrace.review.service.ReviewService;
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
class ReviewIntegrationTest {

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
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void createReviewSuccessfullyUpdatesAvgRating() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);

        JsonNode data = postCreateReview(buyerId, subOrderNo, productId, 5, "非常新鲜",
                List.of("http://img/r1.png")).path("data");
        assertThat(data.path("rating").asInt()).isEqualTo(5);
        assertThat(data.path("images").size()).isEqualTo(1);
        assertThat(data.path("reviewId").asLong()).isPositive();

        assertThat(reviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review>()
                        .eq(Review::getSubOrderId, subOrderIdOf(subOrderNo)))
        ).isEqualTo(1);
        Farmer updated = farmerMapper.selectById(farmer.farmerId());
        assertThat(updated.getAvgRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void reviewOnPendingShipRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOfOrder(orderNo);

        mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(reviewBody(subOrderNo, productId, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));
    }

    @Test
    void reviewOnPendingReceiveRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        SubOrder sub = subOrderOfOrder(orderNo);
        ShipDTO shipDTO = new ShipDTO();
        shipDTO.setLogisticsCompany("顺丰");
        shipDTO.setLogisticsNo("SF123");
        fulfillmentService.ship(farmer.farmerId(), sub.getSubOrderNo(), shipDTO);

        mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(reviewBody(sub.getSubOrderNo(), productId, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));
    }

    @Test
    void reviewByOtherUserRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);

        mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(900002L))
                        .contentType("application/json")
                        .content(reviewBody(subOrderNo, productId, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30026));
    }

    @Test
    void reviewProductNotInSubOrderRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long product1 = createProduct(farmer.farmerId(), "20.00", 100, "芒果1");
        long product2 = createProduct(farmer.farmerId(), "20.00", 100, "芒果2");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, product1);

        mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(reviewBody(subOrderNo, product2, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30041));
    }

    @Test
    void duplicateReviewRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);
        postCreateReview(buyerId, subOrderNo, productId, 5, "好评", List.of());

        mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(reviewBody(subOrderNo, productId, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30038));

        assertThat(reviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review>()
                        .eq(Review::getSubOrderId, subOrderIdOf(subOrderNo)))).isEqualTo(1);
    }

    @Test
    void avgRatingRecalculatedAcrossReviews() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long product1 = createProduct(farmer.farmerId(), "20.00", 100, "芒果1");
        long product2 = createProduct(farmer.farmerId(), "20.00", 100, "芒果2");
        long product3 = createProduct(farmer.farmerId(), "20.00", 100, "芒果3");
        String sub1 = createFinishedSubOrder(buyerId, farmer, product1);
        String sub2 = createFinishedSubOrder(buyerId, farmer, product2);
        String sub3 = createFinishedSubOrder(buyerId, farmer, product3);

        createReviewDirect(buyerId, sub1, product1, 5);
        assertThat(farmerMapper.selectById(farmer.farmerId()).getAvgRating()).isEqualByComparingTo("5.0");

        createReviewDirect(buyerId, sub2, product2, 4);
        assertThat(farmerMapper.selectById(farmer.farmerId()).getAvgRating()).isEqualByComparingTo("4.5");

        createReviewDirect(buyerId, sub3, product3, 3);
        assertThat(farmerMapper.selectById(farmer.farmerId()).getAvgRating()).isEqualByComparingTo("4.0");
        assertThat(reviewMapper.selectAvgRating(farmer.farmerId())).isEqualByComparingTo("4.0");
    }

    @Test
    void replySuccessfully() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);
        long reviewId = createReviewDirect(buyerId, subOrderNo, productId, 5);

        ReviewReplyDTO replyDTO = new ReviewReplyDTO();
        replyDTO.setReply("感谢支持");
        mockMvc.perform(post("/review/{id}/reply", reviewId)
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(replyDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Review review = reviewMapper.selectById(reviewId);
        assertThat(review.getReply()).isEqualTo("感谢支持");
        assertThat(review.getRepliedAt()).isNotNull();
        assertThat(farmerMapper.selectById(farmer.farmerId()).getAvgRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void replyByOtherFarmerRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productId = createProduct(farmerA.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmerA, productId);
        long reviewId = createReviewDirect(buyerId, subOrderNo, productId, 5);

        ReviewReplyDTO replyDTO = new ReviewReplyDTO();
        replyDTO.setReply("越权回复");
        mockMvc.perform(post("/review/{id}/reply", reviewId)
                        .header("Authorization", "Bearer " + token(farmerB.userId()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(replyDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30040));
    }

    @Test
    void replyByNonFarmerForbidden() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);
        long reviewId = createReviewDirect(buyerId, subOrderNo, productId, 5);

        mockMvc.perform(post("/review/{id}/reply", reviewId)
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content("{\"reply\":\"我不是果农\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void replyReviewNotFound() throws Exception {
        FarmerRef farmer = createFarmer();
        mockMvc.perform(post("/review/{id}/reply", 99999999L)
                        .header("Authorization", "Bearer " + token(farmer.userId()))
                        .contentType("application/json")
                        .content("{\"reply\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30039));
    }

    @Test
    void listByProductIdAndFarmerId() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productA = createProduct(farmerA.farmerId(), "20.00", 100, "芒果A");
        long productB = createProduct(farmerB.farmerId(), "20.00", 100, "芒果B");
        String subA = createFinishedSubOrder(buyerId, farmerA, productA);
        String subB = createFinishedSubOrder(buyerId, farmerB, productB);
        createReviewDirect(buyerId, subA, productA, 5);
        createReviewDirect(buyerId, subB, productB, 4);

        JsonNode byProduct = getJson("/review?productId=" + productA, buyerId).path("data");
        assertThat(byProduct.path("total").asLong()).isEqualTo(1);
        assertThat(byProduct.path("records").get(0).path("rating").asInt()).isEqualTo(5);

        JsonNode byFarmer = getJson("/review?farmerId=" + farmerB.farmerId(), buyerId).path("data");
        assertThat(byFarmer.path("total").asLong()).isEqualTo(1);
        assertThat(byFarmer.path("records").get(0).path("rating").asInt()).isEqualTo(4);

        JsonNode both = getJson("/review?productId=" + productA + "&farmerId=" + farmerA.farmerId(), buyerId).path("data");
        assertThat(both.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void listRequiresQueryParam() throws Exception {
        mockMvc.perform(get("/review").header("Authorization", "Bearer " + token(900001L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void listDoesNotReturnDeletedReviews() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);
        long reviewId = createReviewDirect(buyerId, subOrderNo, productId, 5);
        reviewMapper.deleteById(reviewId);

        JsonNode data = getJson("/review?productId=" + productId, buyerId).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(0);
        assertThat(data.path("records").size()).isEqualTo(0);
    }

    @Test
    void createReviewInvalidatesFarmerHomeCache() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);
        stringRedisTemplate.opsForValue().set(CacheKeys.farmerHome(farmer.farmerId()), "1");

        createReviewDirect(buyerId, subOrderNo, productId, 5);

        assertThat(stringRedisTemplate.hasKey(CacheKeys.farmerHome(farmer.farmerId()))).isFalse();
    }

    private long createReviewDirect(long buyerId, String subOrderNo, long productId, int rating) {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setSubOrderNo(subOrderNo);
        dto.setProductId(productId);
        dto.setRating(rating);
        dto.setContent("好评");
        return reviewService.createReview(buyerId, dto).getReviewId();
    }

    private JsonNode postCreateReview(long buyerId, String subOrderNo, long productId, int rating,
                                      String content, List<String> images) throws Exception {
        MvcResult result = mockMvc.perform(post("/review")
                        .header("Authorization", "Bearer " + token(buyerId))
                        .contentType("application/json")
                        .content(reviewBody(subOrderNo, productId, rating, content, images)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String reviewBody(String subOrderNo, long productId, int rating) throws Exception {
        return reviewBody(subOrderNo, productId, rating, null, List.of());
    }

    private String reviewBody(String subOrderNo, long productId, int rating,
                              String content, List<String> images) throws Exception {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setSubOrderNo(subOrderNo);
        dto.setProductId(productId);
        dto.setRating(rating);
        dto.setContent(content);
        dto.setImages(images);
        return objectMapper.writeValueAsString(dto);
    }

    private JsonNode getJson(String url, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long subOrderIdOf(String subOrderNo) {
        return subOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getSubOrderNo, subOrderNo)).getId();
    }

    private SubOrder subOrderOfOrder(String orderNo) {
        return subOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getOrderId, orderIdByNo(orderNo))).get(0);
    }

    private String subOrderNoOfOrder(String orderNo) {
        return subOrderOfOrder(orderNo).getSubOrderNo();
    }

    private long orderIdByNo(String orderNo) {
        return orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)).getId();
    }

    private String createFinishedSubOrder(long buyerId, FarmerRef farmer, long productId) {
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        SubOrder sub = subOrderOfOrder(orderNo);
        ShipDTO shipDTO = new ShipDTO();
        shipDTO.setLogisticsCompany("顺丰");
        shipDTO.setLogisticsNo("SF123");
        fulfillmentService.ship(farmer.farmerId(), sub.getSubOrderNo(), shipDTO);
        fulfillmentService.receive(buyerId, sub.getSubOrderNo());
        return sub.getSubOrderNo();
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

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }
}
