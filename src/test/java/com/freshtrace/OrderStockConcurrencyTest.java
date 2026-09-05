package com.freshtrace;

import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.service.OrderService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发下单防超卖：MySQL stock=5，10 个线程各买 1（各自独立的购物车项/幂等 token），
 * 必须恰好成功 5 单，MySQL/Redis 库存均不为负，且无半个订单。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OrderStockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductAttributeMapper productAttributeMapper;

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
    void concurrentOrdersDoNotOversell() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProduct(farmerId, "20.00", 5);
        long addressId = createAddress(userId);

        int threads = 10;
        List<Long> cartIds = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            cartIds.add(addCart(userId, productId, 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger stockNotEnough = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        try {
            for (Long cartId : cartIds) {
                pool.submit(() -> {
                    try {
                        start.await();
                        CreateOrderDTO dto = new CreateOrderDTO();
                        dto.setAddressId(addressId);
                        dto.setSelectedCartIds(List.of(cartId));
                        orderService.createOrder(userId, dto, "tok-" + UUID.randomUUID());
                        success.incrementAndGet();
                    } catch (BizException e) {
                        if (ErrorCode.STOCK_NOT_ENOUGH.getCode().equals(e.getCode())) {
                            stockNotEnough.incrementAndGet();
                        } else {
                            unexpected.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpected.incrementAndGet();
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

        assertThat(unexpected.get()).as("不应出现库存不足之外的异常").isZero();
        assertThat(success.get()).isEqualTo(5);
        assertThat(stockNotEnough.get()).isEqualTo(5);

        // 库存不为负
        Product product = productMapper.selectById(productId);
        assertThat(product.getStock()).isZero();
        String redisStock = stringRedisTemplate.opsForValue().get("stock:product:" + productId);
        assertThat(redisStock).isNotNull();
        assertThat(Integer.parseInt(redisStock)).isGreaterThanOrEqualTo(0);
        assertThat(Integer.parseInt(redisStock)).isZero();

        // 订单数据完整：5 单，无半个订单
        assertThat(count("SELECT COUNT(*) FROM t_order")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_sub_order")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_order_item")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_payment")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 1")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM t_shopping_cart WHERE deleted = 0")).isEqualTo(5);
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

        for (int i = 1; i <= 10; i++) {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(product.getId());
            attribute.setAttrName("规格");
            attribute.setAttrValue(i + "斤装");
            attribute.setExtraPrice(BigDecimal.ZERO);
            productAttributeMapper.insert(attribute);
        }
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
        long specIndex = seq.incrementAndGet() % 10 + 1;
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSpecSnapshot("[{\"name\":\"规格\",\"value\":\"" + specIndex + "斤装\"}]");
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        return cart.getId();
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
