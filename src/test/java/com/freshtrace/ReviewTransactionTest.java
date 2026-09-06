package com.freshtrace;

import com.freshtrace.common.BizException;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.review.dto.ReviewCreateDTO;
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
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 评价事务一致性测试：Review INSERT 与 Farmer.avgRating 更新处于同一本地事务，
 * AVG 聚合失败时整体回滚，不留「评价存在但评分未同步」的半成功状态。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ReviewTransactionTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private FulfillmentService fulfillmentService;

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
    private SubOrderMapper subOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    @MockitoSpyBean
    private ReviewMapper reviewMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    private record FarmerRef(Long farmerId, Long userId) {
    }

    @Test
    void avgRatingFailureRollsBackReviewInsert() {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);

        BDDMockito.given(reviewMapper.selectAvgRating(anyLong()))
                .willThrow(new RuntimeException("db failure"));

        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setSubOrderNo(subOrderNo);
        dto.setProductId(productId);
        dto.setRating(5);

        assertThatThrownBy(() -> reviewService.createReview(buyerId, dto))
                .isInstanceOf(RuntimeException.class);

        Long count = reviewMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review>()
                        .eq(Review::getSubOrderId, subOrderIdOf(subOrderNo)));
        assertThat(count).isZero();
        assertThat(farmerMapper.selectById(farmer.farmerId()).getAvgRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void duplicateKeyConvertedToReviewExistsException() {
        long buyerId = 900001L;
        FarmerRef farmer = createFarmer();
        long productId = createProduct(farmer.farmerId(), "20.00", 100, "海南芒果");
        String subOrderNo = createFinishedSubOrder(buyerId, farmer, productId);

        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setSubOrderNo(subOrderNo);
        dto.setProductId(productId);
        dto.setRating(5);
        reviewService.createReview(buyerId, dto);

        ReviewCreateDTO duplicate = new ReviewCreateDTO();
        duplicate.setSubOrderNo(subOrderNo);
        duplicate.setProductId(productId);
        duplicate.setRating(4);

        assertThatThrownBy(() -> reviewService.createReview(buyerId, duplicate))
                .isInstanceOf(BizException.class)
                .hasMessage("该商品已评价，请勿重复评价");
    }

    private long subOrderIdOf(String subOrderNo) {
        return subOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getSubOrderNo, subOrderNo)).getId();
    }

    private String createFinishedSubOrder(long buyerId, FarmerRef farmer, long productId) {
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 1)));
        pay(buyerId, orderNo);
        SubOrder sub = subOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getOrderId, orderIdByNo(orderNo))).get(0);
        ShipDTO shipDTO = new ShipDTO();
        shipDTO.setLogisticsCompany("顺丰");
        shipDTO.setLogisticsNo("SF123");
        fulfillmentService.ship(farmer.farmerId(), sub.getSubOrderNo(), shipDTO);
        fulfillmentService.receive(buyerId, sub.getSubOrderNo());
        return sub.getSubOrderNo();
    }

    private long orderIdByNo(String orderNo) {
        return orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)).getId();
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
}
