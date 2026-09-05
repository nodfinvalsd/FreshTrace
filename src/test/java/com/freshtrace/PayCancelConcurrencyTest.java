package com.freshtrace;

import com.freshtrace.common.BizException;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.service.OrderCancelService;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.trade.vo.CancelOrderVO;
import com.freshtrace.trade.vo.PaymentResultVO;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付 vs 取消并发仲裁：10 线程（5 支付 + 5 取消）同时操作同一待支付订单。
 * 最终状态必须二选一（PAID 或 CANCELLED），支付与取消的最终结果互斥，
 * 不允许 Payment=SUCCESS + Order=CANCELLED 或 Payment=PENDING + Order=PAID，
 * 库存要么不恢复（支付赢）、要么恰好恢复一次（取消赢）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PayCancelConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderCancelService orderCancelService;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void payAndCancelConcurrentlyArbitrateToSingleWinner() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 100);
        long addressId = createAddress(userId);
        long cartId = addCart(userId, productId, 2);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);

        CreateOrderDTO createDTO = new CreateOrderDTO();
        createDTO.setAddressId(addressId);
        createDTO.setSelectedCartIds(List.of(cartId));
        String orderNo = orderService.createOrder(userId, createDTO, "tok-" + UUID.randomUUID()).getOrderNo();
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(98);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Object> payResults = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Object> cancelResults = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < 5; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        PayOrderDTO dto = new PayOrderDTO();
                        dto.setOrderNo(orderNo);
                        payResults.add(paymentService.pay(userId, dto, "tok-" + UUID.randomUUID()));
                    } catch (BizException e) {
                        payResults.add(e);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            for (int i = 0; i < 5; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        cancelResults.add(orderCancelService.cancelByUser(userId, orderNo));
                    } catch (BizException e) {
                        cancelResults.add(e);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdown();
        }

        assertThat(errors.get()).as("不应出现非业务异常").isZero();

        long paySuccesses = payResults.stream().filter(PaymentResultVO.class::isInstance).count();
        long cancelSuccesses = cancelResults.stream().filter(CancelOrderVO.class::isInstance).count();

        int orderStatus = queryInt("SELECT status FROM t_order");
        int paymentStatus = queryInt("SELECT status FROM t_payment");
        int subOrderStatus = queryInt("SELECT status FROM t_sub_order");
        int mysqlStock = productMapper.selectById(productId).getStock();
        String redisStock = stringRedisTemplate.opsForValue().get("stock:product:" + productId);

        if (paySuccesses == 5) {
            // 支付胜出：其余支付幂等成功、全部取消被拒绝
            assertThat(cancelSuccesses).isZero();
            assertThat(orderStatus).isEqualTo(1);
            assertThat(paymentStatus).isEqualTo(1);
            assertThat(subOrderStatus).isEqualTo(1);
            assertThat(mysqlStock).isEqualTo(98);
            assertThat(redisStock).isEqualTo("98");
        } else if (cancelSuccesses == 5) {
            // 取消胜出：其余取消幂等成功、全部支付被拒绝
            assertThat(paySuccesses).isZero();
            assertThat(orderStatus).isEqualTo(5);
            assertThat(paymentStatus).isEqualTo(2);
            assertThat(subOrderStatus).isEqualTo(6);
            assertThat(mysqlStock).isEqualTo(100);
            assertThat(redisStock).isEqualTo("100");
        } else {
            throw new AssertionError("支付与取消并发结果不符合任一互斥终态: pay="
                    + paySuccesses + ", cancel=" + cancelSuccesses);
        }

        // 禁止的脏状态组合
        assertThat(paymentStatus == 1 && orderStatus == 5).as("禁止 Payment=SUCCESS + Order=CANCELLED").isFalse();
        assertThat(paymentStatus == 0 && orderStatus == 1).as("禁止 Payment=PENDING + Order=PAID").isFalse();
        // 库存没有负值
        assertThat(mysqlStock).isGreaterThanOrEqualTo(0);
        assertThat(Integer.parseInt(redisStock)).isGreaterThanOrEqualTo(0);
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

    private long createProduct(long farmerId, String price, int stock) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("海南芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle("海南芒果 " + n);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setUnit("斤");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long createAddress(long userId) {
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13812345678");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园1栋");
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

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
