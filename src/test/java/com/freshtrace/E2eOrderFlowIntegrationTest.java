package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.review.dto.ReviewCreateDTO;
import com.freshtrace.review.entity.Review;
import com.freshtrace.review.mapper.ReviewMapper;
import com.freshtrace.trade.dto.CartAddDTO;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.user.dto.AddressDTO;
import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.RegisterDTO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11 端到端全流程测试。
 * <p>
 * 覆盖：注册 → 登录 → 果农认证（申请+审核）→ 上架商品（创建+审核）→
 * 加购 → 下单 → 支付 → 发货 → 收货 → 评价，全部走 HTTP 接口。
 * 买家与果农使用两个账号（认证通过后果农与买家身份互斥）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class E2eOrderFlowIntegrationTest {

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
    private SubOrderMapper subOrderMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Test
    void fullTradeFlowFromRegisterToReview() throws Exception {
        Account buyer = registerAndLogin("buyer_e2e", "13811110001");
        Account farmer = registerAndLogin("farmer_e2e", "13811110002");
        String adminToken = createAdminToken();

        long categoryId = createCategory(adminToken, "苹果");
        long spuId = createSpu(adminToken, categoryId, "烟台红富士");

        applyFarmer(farmer.token());
        long farmerId = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, farmer.userId())).getId();
        auditFarmer(adminToken, farmerId);

        long productId = createProduct(farmer.token(), spuId, "烟台红富士苹果 5斤装");
        auditProduct(adminToken, productId);

        long addressId = createAddress(buyer.token());
        long cartId = addCart(buyer.token(), productId);

        JsonNode order = postJson("/order", buyer.token(), createOrderBody(addressId, cartId));
        String orderNo = order.path("data").path("orderNo").asText();
        String subOrderNo = order.path("data").path("subOrders").get(0).path("subOrderNo").asText();
        assertThat(orderNo).isNotBlank();
        assertThat(subOrderNo).isNotBlank();

        JsonNode payResult = postJson("/payment/pay", buyer.token(), payBody(orderNo));
        assertThat(payResult.path("data").path("status").asInt()).isEqualTo(1);
        ship(farmer.token(), subOrderNo);
        receive(buyer.token(), subOrderNo);
        review(buyer.token(), subOrderNo, productId);

        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.FINISHED.getCode());
        assertThat(subOrder.getReceivedAt()).isNotNull();

        Review savedReview = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId));
        assertThat(savedReview).isNotNull();
        assertThat(savedReview.getRating()).isEqualTo(5);

        JsonNode detail = getJson("/order/" + orderNo, buyer.token());
        assertThat(detail.path("data").path("status").asInt()).isEqualTo(OrderStatus.FINISHED.getCode());
    }

    private record Account(long userId, String token) {
    }

    private Account registerAndLogin(String username, String phone) throws Exception {
        RegisterDTO register = new RegisterDTO();
        register.setUsername(username);
        register.setPassword("123456");
        register.setPhone(phone);
        register.setNickname(username);
        JsonNode registered = postJson("/user/register", null, register);
        long userId = registered.path("data").path("id").asLong();

        LoginDTO login = new LoginDTO();
        login.setAccount(username);
        login.setPassword("123456");
        JsonNode loggedIn = postJson("/user/login", null, login);
        String token = loggedIn.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
        return new Account(userId, token);
    }

    private String createAdminToken() {
        User admin = new User();
        admin.setUsername("admin_e2e");
        admin.setPasswordHash("x");
        admin.setPhone("13911110000");
        admin.setRole(1);
        admin.setStatus(1);
        userMapper.insert(admin);
        return jwtUtils.generateAccessToken(admin.getId(), 1);
    }

    private long createCategory(String adminToken, String name) throws Exception {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName(name);
        dto.setSortOrder(0);
        return postJson("/category", adminToken, dto).path("data").path("id").asLong();
    }

    private long createSpu(String adminToken, long categoryId, String name) throws Exception {
        SpuCreateDTO dto = new SpuCreateDTO();
        dto.setCategoryId(categoryId);
        dto.setName(name);
        dto.setStatus(1);
        return postJson("/spu", adminToken, dto).path("data").path("id").asLong();
    }

    private void applyFarmer(String farmerToken) throws Exception {
        FarmerApplyDTO dto = new FarmerApplyDTO();
        dto.setRealName("李四");
        dto.setIdCard("110101199001011234");
        dto.setOrchardName("烟台苹果园");
        dto.setOrchardProvince("山东省");
        dto.setOrchardCity("烟台市");
        dto.setOrchardDistrict("栖霞市");
        dto.setOrchardAddress("某果园1号");
        postJson("/farmer/apply", farmerToken, dto);
    }

    private void auditFarmer(String adminToken, long farmerId) throws Exception {
        FarmerAuditDTO dto = new FarmerAuditDTO();
        dto.setFarmerId(farmerId);
        dto.setAuditStatus(1);
        postJson("/admin/farmer/audit", adminToken, dto);
    }

    private long createProduct(String farmerToken, long spuId, String title) throws Exception {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setSpuId(spuId);
        dto.setTitle(title);
        dto.setDescription(title + " 描述");
        dto.setPrice(new BigDecimal("39.90"));
        dto.setStock(100);
        dto.setUnit("箱");
        dto.setMainImage("http://img/e2e.jpg");
        dto.setLifecycle(3);
        return postJson("/product", farmerToken, dto).path("data").path("id").asLong();
    }

    private void auditProduct(String adminToken, long productId) throws Exception {
        ProductAuditDTO dto = new ProductAuditDTO();
        dto.setAuditStatus(1);
        postJson("/admin/product/" + productId + "/audit", adminToken, dto);
    }

    private long createAddress(String buyerToken) throws Exception {
        AddressDTO dto = new AddressDTO();
        dto.setReceiverName("王五");
        dto.setReceiverPhone("13811110001");
        dto.setProvince("广东省");
        dto.setCity("深圳市");
        dto.setDistrict("南山区");
        dto.setDetail("科技园1栋");
        dto.setIsDefault(1);
        return postJson("/user/address", buyerToken, dto).path("data").path("id").asLong();
    }

    private long addCart(String buyerToken, long productId) throws Exception {
        CartAddDTO dto = new CartAddDTO();
        dto.setProductId(productId);
        dto.setQuantity(2);
        return postJson("/cart", buyerToken, dto).path("data").path("cartId").asLong();
    }

    private CreateOrderDTO createOrderBody(long addressId, long cartId) {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setAddressId(addressId);
        dto.setSelectedCartIds(List.of(cartId));
        return dto;
    }

    private PayOrderDTO payBody(String orderNo) {
        PayOrderDTO dto = new PayOrderDTO();
        dto.setOrderNo(orderNo);
        return dto;
    }

    private void ship(String farmerToken, String subOrderNo) throws Exception {
        ShipDTO dto = new ShipDTO();
        dto.setLogisticsCompany("顺丰");
        dto.setLogisticsNo("SF" + System.currentTimeMillis());
        postJson("/farmer/sub-order/" + subOrderNo + "/ship", farmerToken, dto);
    }

    private void receive(String buyerToken, String subOrderNo) throws Exception {
        postJson("/sub-order/" + subOrderNo + "/receive", buyerToken, null);
    }

    private void review(String buyerToken, String subOrderNo, long productId) throws Exception {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setSubOrderNo(subOrderNo);
        dto.setProductId(productId);
        dto.setRating(5);
        dto.setContent("苹果很新鲜，物流很快！");
        postJson("/review", buyerToken, dto);
    }

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        var request = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "e2e-" + UUID.randomUUID());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
