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
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.trade.service.RefundService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderQueryIntegrationTest {

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

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void listReturnsPagedOrdersWithSubOrdersAndItems() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productA = createProduct(farmerId, "20.00", 100, "海南芒果A");
        long productB = createProduct(farmerId, "20.00", 100, "海南芒果B");
        long productC = createProduct(farmerId, "20.00", 100, "海南芒果C");
        long addressId = createAddress(userId, "科技园1栋");

        String order1 = createOrder(userId, addressId,
                List.of(addCart(userId, productA, 2), addCart(userId, productB, 1)));
        String order2 = createOrder(userId, addressId, List.of(addCart(userId, productC, 1)));

        JsonNode data = getJson("/order?page=1&size=10", userId).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(2);
        assertThat(data.path("records").size()).isEqualTo(2);

        JsonNode record1 = findRecord(data, order1);
        assertThat(record1.path("statusDesc").asText()).isEqualTo("待支付");
        assertThat(record1.path("subOrders").size()).isEqualTo(1);
        JsonNode sub1 = record1.path("subOrders").get(0);
        assertThat(sub1.path("farmerId").asLong()).isEqualTo(farmerId);
        assertThat(sub1.path("amount").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(sub1.path("items").size()).isEqualTo(2);
        assertThat(sub1.path("items").get(0).path("productTitle").asText()).isNotBlank();
        assertThat(sub1.path("items").get(0).path("quantity").asInt()).isPositive();

        JsonNode record2 = findRecord(data, order2);
        assertThat(record2.path("subOrders").get(0).path("items").size()).isEqualTo(1);
    }

    @Test
    void statusFilterWorks() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long product1 = createProduct(farmerId, "20.00", 100, "海南芒果1");
        long product2 = createProduct(farmerId, "20.00", 100, "海南芒果2");
        long product3 = createProduct(farmerId, "20.00", 100, "海南芒果3");
        long addressId = createAddress(userId, "科技园1栋");

        String pending = createOrder(userId, addressId, List.of(addCart(userId, product1, 1)));
        String paid = createOrder(userId, addressId, List.of(addCart(userId, product2, 1)));
        pay(userId, paid);
        String cancelled = createOrder(userId, addressId, List.of(addCart(userId, product3, 1)));
        cancel(userId, cancelled);

        assertThat(getJson("/order?status=0", userId).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(getJson("/order?status=0", userId).path("data").path("records").get(0).path("orderNo").asText())
                .isEqualTo(pending);
        assertThat(getJson("/order?status=1", userId).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(getJson("/order?status=1", userId).path("data").path("records").get(0).path("orderNo").asText())
                .isEqualTo(paid);
        assertThat(getJson("/order?status=5", userId).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(getJson("/order?status=5", userId).path("data").path("records").get(0).path("orderNo").asText())
                .isEqualTo(cancelled);
        assertThat(getJson("/order", userId).path("data").path("total").asLong()).isEqualTo(3);
    }

    @Test
    void invalidStatusRejected() throws Exception {
        mockMvc.perform(get("/order").param("status", "99")
                        .header("Authorization", "Bearer " + token(900001L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void paginationDoesNotOverlap() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long addressId = createAddress(userId, "科技园1栋");
        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long productId = createProduct(farmerId, "20.00", 100, "海南芒果" + i);
            orderNos.add(createOrder(userId, addressId, List.of(addCart(userId, productId, 1))));
        }

        String page1No = getJson("/order?page=1&size=1", userId).path("data").path("records").get(0).path("orderNo").asText();
        String page2No = getJson("/order?page=2&size=1", userId).path("data").path("records").get(0).path("orderNo").asText();
        String page3No = getJson("/order?page=3&size=1", userId).path("data").path("records").get(0).path("orderNo").asText();

        assertThat(List.of(page1No, page2No, page3No)).containsExactlyInAnyOrderElementsOf(orderNos);
        assertThat(getJson("/order?page=1&size=1", userId).path("data").path("total").asLong()).isEqualTo(3);
    }

    @Test
    void emptyListReturnsEmpty() throws Exception {
        mockMvc.perform(get("/order")
                        .header("Authorization", "Bearer " + token(900001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void listIsIsolatedBetweenUsers() throws Exception {
        long userA = 900001L;
        long userB = 900002L;
        long farmerId = createFarmer();
        long product1 = createProduct(farmerId, "20.00", 100, "海南芒果1");
        long product2 = createProduct(farmerId, "20.00", 100, "海南芒果2");
        long product3 = createProduct(farmerId, "20.00", 100, "海南芒果3");
        long addressA = createAddress(userA, "科技园1栋");
        long addressB = createAddress(userB, "科技园2栋");
        createOrder(userA, addressA, List.of(addCart(userA, product1, 1)));
        createOrder(userA, addressA, List.of(addCart(userA, product2, 1)));
        createOrder(userB, addressB, List.of(addCart(userB, product3, 1)));

        assertThat(getJson("/order", userA).path("data").path("total").asLong()).isEqualTo(2);
        assertThat(getJson("/order", userB).path("data").path("total").asLong()).isEqualTo(1);
    }

    @Test
    void detailOfOthersOrderRejected() throws Exception {
        long userA = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        String orderNo = createOrder(userA, createAddress(userA, "科技园1栋"),
                List.of(addCart(userA, productId, 1)));

        mockMvc.perform(get("/order/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + token(900002L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30025));
    }

    @Test
    void unauthRejected() throws Exception {
        mockMvc.perform(get("/order")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/order/O-x")).andExpect(status().isUnauthorized());
    }

    @Test
    void addressSnapshotIsImmutable() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        long addressId = createAddress(userId, "科技园1栋");
        String orderNo = createOrder(userId, addressId, List.of(addCart(userId, productId, 1)));

        Address address = addressMapper.selectById(addressId);
        address.setDetail("新地址99号");
        addressMapper.updateById(address);

        JsonNode detail = getJson("/order/" + orderNo, userId).path("data");
        assertThat(detail.path("addressSnapshot").path("detail").asText()).isEqualTo("科技园1栋");
        assertThat(detail.path("addressSnapshot").path("receiverName").asText()).isEqualTo("张三");
    }

    @Test
    void productSnapshotIsImmutableAndAmountsNotRecalculated() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果X");
        long addressId = createAddress(userId, "科技园1栋");
        String orderNo = createOrder(userId, addressId, List.of(addCart(userId, productId, 2)));

        Product product = productMapper.selectById(productId);
        product.setTitle("改名后的芒果");
        product.setPrice(new BigDecimal("99.00"));
        product.setMainImage("http://img/new.png");
        productMapper.updateById(product);

        JsonNode detail = getJson("/order/" + orderNo, userId).path("data");
        JsonNode item = detail.path("subOrders").get(0).path("items").get(0);
        assertThat(item.path("productTitle").asText()).isEqualTo("海南芒果X");
        assertThat(item.path("productImage").asText()).isEqualTo("http://img/x.png");
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(item.path("totalPrice").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(detail.path("totalAmount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(detail.path("payAmount").decimalValue()).isEqualByComparingTo("40.00");
    }

    @Test
    void refundIsNullWhenNoRefund() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        String orderNo = createOrder(userId, createAddress(userId, "科技园1栋"),
                List.of(addCart(userId, productId, 1)));

        JsonNode detail = getJson("/order/" + orderNo, userId).path("data");
        assertThat(detail.path("subOrders").get(0).path("refund").isNull()).isTrue();
    }

    @Test
    void refundShownInDetail() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        String orderNo = createOrder(userId, createAddress(userId, "科技园1栋"),
                List.of(addCart(userId, productId, 2)));
        pay(userId, orderNo);
        String subOrderNo = getJson("/order/" + orderNo, userId)
                .path("data").path("subOrders").get(0).path("subOrderNo").asText();
        RefundApplyDTO refundDTO = new RefundApplyDTO();
        refundDTO.setReason("商品不符合预期");
        refundService.refund(userId, subOrderNo, refundDTO);

        JsonNode sub = getJson("/order/" + orderNo, userId).path("data").path("subOrders").get(0);
        assertThat(sub.path("refund").isNull()).isFalse();
        assertThat(sub.path("refund").path("status").asInt()).isEqualTo(4);
        assertThat(sub.path("refund").path("statusDesc").asText()).isEqualTo("已退款");
        assertThat(sub.path("refund").path("amount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(sub.path("refund").path("reason").asText()).isEqualTo("商品不符合预期");
        assertThat(sub.path("status").asInt()).isEqualTo(5);
    }

    @Test
    void complexOrderAssemblyIsCorrect() throws Exception {
        long userId = 900001L;
        long farmerA = createFarmer();
        long farmerB = createFarmer();
        long productA1 = createProduct(farmerA, "10.00", 100, "芒果A1");
        long productA2 = createProduct(farmerA, "20.00", 100, "芒果A2");
        long productB1 = createProduct(farmerB, "30.00", 100, "芒果B1");
        long productB2 = createProduct(farmerB, "40.00", 100, "芒果B2");
        long addressId = createAddress(userId, "科技园1栋");
        String orderNo = createOrder(userId, addressId, List.of(
                addCart(userId, productA1, 1), addCart(userId, productA2, 1),
                addCart(userId, productB1, 1), addCart(userId, productB2, 1)));

        JsonNode detail = getJson("/order/" + orderNo, userId).path("data");
        assertThat(detail.path("subOrders").size()).isEqualTo(2);
        assertThat(detail.path("totalAmount").decimalValue()).isEqualByComparingTo("100.00");

        JsonNode subA = findByFarmer(detail.path("subOrders"), farmerA);
        JsonNode subB = findByFarmer(detail.path("subOrders"), farmerB);
        assertThat(subA.path("items").size()).isEqualTo(2);
        assertThat(subB.path("items").size()).isEqualTo(2);
        assertThat(subA.path("amount").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(subB.path("amount").decimalValue()).isEqualByComparingTo("70.00");
        List<String> titlesA = List.of(subA.path("items").get(0).path("productTitle").asText(),
                subA.path("items").get(1).path("productTitle").asText());
        assertThat(titlesA).containsExactlyInAnyOrder("芒果A1", "芒果A2");
        List<String> titlesB = List.of(subB.path("items").get(0).path("productTitle").asText(),
                subB.path("items").get(1).path("productTitle").asText());
        assertThat(titlesB).containsExactlyInAnyOrder("芒果B1", "芒果B2");
    }

    @Test
    void paymentShownInDetail() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        String orderNo = createOrder(userId, createAddress(userId, "科技园1栋"),
                List.of(addCart(userId, productId, 2)));
        pay(userId, orderNo);

        JsonNode payment = getJson("/order/" + orderNo, userId).path("data").path("payment");
        assertThat(payment.isNull()).isFalse();
        assertThat(payment.path("status").asInt()).isEqualTo(1);
        assertThat(payment.path("statusDesc").asText()).isEqualTo("支付成功");
        assertThat(payment.path("payMethod").asText()).isEqualTo("SIMULATED");
        assertThat(payment.path("amount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(payment.path("paidAt").asText()).isNotBlank();
        assertThat(payment.path("paymentNo").asText()).startsWith("P");
    }

    private JsonNode findByFarmer(JsonNode subOrders, long farmerId) {
        for (JsonNode sub : subOrders) {
            if (sub.path("farmerId").asLong() == farmerId) {
                return sub;
            }
        }
        throw new AssertionError("sub order not found for farmer " + farmerId);
    }

    private JsonNode findRecord(JsonNode data, String orderNo) {
        for (JsonNode record : data.path("records")) {
            if (orderNo.equals(record.path("orderNo").asText())) {
                return record;
            }
        }
        throw new AssertionError("order not found in page: " + orderNo);
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

    private void cancel(long userId, String orderNo) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/order/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk());
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
