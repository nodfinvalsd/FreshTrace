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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发货并发测试：两个请求同时发货同一 PENDING_SHIP 子订单，
 * 条件更新抢占保证仅一个成功，数据库终态为 PENDING_RECEIVE。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ShipConcurrencyTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void concurrentShipOnlyOneSucceeds() throws Exception {
        long buyerId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100, "海南芒果");
        String orderNo = createOrder(buyerId, createAddress(buyerId, "科技园1栋"),
                List.of(addCart(buyerId, productId, 2)));
        pay(buyerId, orderNo);

        SubOrder subOrder = subOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getOrderId, orderIdByNo(orderNo))).get(0);
        assertThat(subOrder.getStatus()).isEqualTo(1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> f1 = executor.submit(() -> shipWhenSignaled(farmerId, subOrder.getSubOrderNo(), "顺丰", "SF111", ready, start));
            Future<Object> f2 = executor.submit(() -> shipWhenSignaled(farmerId, subOrder.getSubOrderNo(), "中通", "ZT222", ready, start));
            ready.await();
            start.countDown();

            Object r1 = f1.get();
            Object r2 = f2.get();
            long successCount = List.of(r1, r2).stream()
                    .filter(r -> !(r instanceof BizException))
                    .count();
            long failureCount = List.of(r1, r2).stream()
                    .filter(BizException.class::isInstance)
                    .count();
            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        SubOrder latest = subOrderMapper.selectById(subOrder.getId());
        assertThat(latest.getStatus()).isEqualTo(2);
        assertThat(latest.getShippedAt()).isNotNull();
        assertThat(latest.getLogisticsCompany()).isIn("顺丰", "中通");

        Order order = orderMapper.selectById(latest.getOrderId());
        assertThat(order.getStatus()).isEqualTo(3);
    }

    private Object shipWhenSignaled(long farmerId, String subOrderNo, String company, String logisticsNo,
                                    CountDownLatch ready, CountDownLatch start) {
        ShipDTO dto = new ShipDTO();
        dto.setLogisticsCompany(company);
        dto.setLogisticsNo(logisticsNo);
        ready.countDown();
        try {
            start.await();
            return fulfillmentService.ship(farmerId, subOrderNo, dto);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (BizException e) {
            return e;
        }
    }

    private long orderIdByNo(String orderNo) {
        Order order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo));
        return order.getId();
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
}
