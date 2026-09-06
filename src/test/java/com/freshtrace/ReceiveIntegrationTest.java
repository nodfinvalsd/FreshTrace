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
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.trade.service.RefundService;
import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
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
class ReceiveIntegrationTest {

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
    private RefundService refundService;

    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void receiveSuccessfullyFinishesSubOrderAndMainOrder() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 2)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOf(orderNo, buyerId, farmer.farmerId());
        ship(farmer.userId(), subOrderNo);

        JsonNode data = receive(buyerId, subOrderNo).path("data");
        assertThat(data.path("subOrderNo").asText()).isEqualTo(subOrderNo);
        assertThat(data.path("status").asInt()).isEqualTo(3);
        assertThat(data.path("statusDesc").asText()).isEqualTo("已完成");
        assertThat(data.path("receivedAt").asText()).isNotBlank();

        SubOrder sub = subOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getSubOrderNo, subOrderNo));
        assertThat(sub.getStatus()).isEqualTo(3);
        assertThat(sub.getReceivedAt()).isNotNull();

        JsonNode order = getJson("/order/" + orderNo, buyerId).path("data");
        assertThat(order.path("status").asInt()).isEqualTo(4);
        assertThat(order.path("statusDesc").asText()).isEqualTo("已完成");
        assertThat(order.path("finishedAt").asText()).isNotBlank();

        Order orderEntity = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo));
        assertThat(orderEntity.getFinishedAt()).isNotNull();
    }

    @Test
    void receiveByOtherBuyerRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOf(orderNo, buyerId, farmer.farmerId());
        ship(farmer.userId(), subOrderNo);

        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", subOrderNo)
                        .header("Authorization", "Bearer " + token(900002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30026));
    }

    @Test
    void receiveBeforeShipRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOf(orderNo, buyerId, farmer.farmerId());

        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", subOrderNo)
                        .header("Authorization", "Bearer " + token(buyerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));
    }

    @Test
    void receiveTwiceRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOf(orderNo, buyerId, farmer.farmerId());
        ship(farmer.userId(), subOrderNo);
        receive(buyerId, subOrderNo);

        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", subOrderNo)
                        .header("Authorization", "Bearer " + token(buyerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));
    }

    @Test
    void receiveRefundingRejected() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        String subOrderNo = subOrderNoOf(orderNo, buyerId, farmer.farmerId());
        RefundApplyDTO refundDTO = new RefundApplyDTO();
        refundDTO.setReason("商品不符合预期");
        refundService.refund(buyerId, subOrderNo, refundDTO);

        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", subOrderNo)
                        .header("Authorization", "Bearer " + token(buyerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30029));
    }

    @Test
    void receiveSubOrderNotFound() throws Exception {
        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", "S-not-exist")
                        .header("Authorization", "Bearer " + token(900001L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30028));
    }

    @Test
    void receiveUnauthRejected() throws Exception {
        mockMvc.perform(post("/sub-order/{subOrderNo}/receive", "S-any"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mainOrderFinishesOnlyWhenAllSubOrdersFinished() throws Exception {
        long buyerId = 900001L;
        FarmerRef farmerA = createFarmer();
        FarmerRef farmerB = createFarmer();
        long productA = createProduct(farmerA.farmerId(), "10.00", 100, "芒果A");
        long productB = createProduct(farmerB.farmerId(), "20.00", 100, "芒果B");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productA, 1), addCart(buyerId, productB, 1)));
        pay(buyerId, orderNo);
        String subA = subOrderNoOf(orderNo, buyerId, farmerA.farmerId());
        String subB = subOrderNoOf(orderNo, buyerId, farmerB.farmerId());
        ship(farmerA.userId(), subA);
        ship(farmerB.userId(), subB);

        receive(buyerId, subA);
        JsonNode orderAfterA = getJson("/order/" + orderNo, buyerId).path("data");
        assertThat(orderAfterA.path("status").asInt()).isEqualTo(3);
        assertThat(orderAfterA.path("finishedAt").isNull()).isTrue();

        receive(buyerId, subB);
        JsonNode orderAfterB = getJson("/order/" + orderNo, buyerId).path("data");
        assertThat(orderAfterB.path("status").asInt()).isEqualTo(4);
        assertThat(orderAfterB.path("statusDesc").asText()).isEqualTo("已完成");
        assertThat(orderAfterB.path("finishedAt").asText()).isNotBlank();
    }

    private JsonNode receive(long buyerId, String subOrderNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/sub-order/{subOrderNo}/receive", subOrderNo)
                        .header("Authorization", "Bearer " + token(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void ship(long farmerUserId, String subOrderNo) throws Exception {
        mockMvc.perform(post("/farmer/sub-order/{subOrderNo}/ship", subOrderNo)
                        .header("Authorization", "Bearer " + token(farmerUserId))
                        .contentType("application/json")
                        .content("{\"logisticsCompany\":\"顺丰\",\"logisticsNo\":\"SF123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String subOrderNoOf(String orderNo, long buyerId, long farmerId) throws Exception {
        JsonNode subOrders = getJson("/order/" + orderNo, buyerId).path("data").path("subOrders");
        for (JsonNode sub : subOrders) {
            if (sub.path("farmerId").asLong() == farmerId) {
                return sub.path("subOrderNo").asText();
            }
        }
        throw new AssertionError("sub order not found for farmer " + farmerId);
    }

    private JsonNode getJson(String url, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
