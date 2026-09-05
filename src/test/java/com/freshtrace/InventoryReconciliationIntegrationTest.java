package com.freshtrace;

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
import com.freshtrace.trade.service.InventoryReconciliationService;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.support.ReconciliationResult;
import com.freshtrace.user.entity.Address;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.AddressMapper;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class InventoryReconciliationIntegrationTest {

    @Autowired
    private InventoryReconciliationService reconciliationService;

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
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void consistentStockNotRepaired() {
        long productId = createProduct("20.00", 100, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "100");

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getScanned()).isEqualTo(1);
        assertThat(result.getConsistent()).isEqualTo(1);
        assertThat(result.getRepaired()).isZero();
        assertThat(result.getRepairFailed()).isZero();
        assertThat(redisStock(productId)).isEqualTo("100");
    }

    @Test
    void redisHigherRepairedToMysql() {
        long productId = createProduct("20.00", 95, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "100");

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getMismatch()).isEqualTo(1);
        assertThat(result.getRepaired()).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(95);
        assertThat(redisStock(productId)).isEqualTo("95");
    }

    @Test
    void redisLowerRepairedToMysql() {
        long productId = createProduct("20.00", 95, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "90");

        reconciliationService.scanAndRepair();

        assertThat(redisStock(productId)).isEqualTo("95");
    }

    @Test
    void missingKeyInitializedFromMysql() {
        long productId = createProduct("20.00", 50, Map.of());
        stringRedisTemplate.delete("stock:product:" + productId);

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getMissing()).isEqualTo(1);
        assertThat(result.getRepaired()).isEqualTo(1);
        assertThat(redisStock(productId)).isEqualTo("50");
    }

    @Test
    void nonNumericRedisNotFatalAndUntouched() {
        long productId = createProduct("20.00", 100, Map.of());
        long otherProductId = createProduct("20.00", 200, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "abc");
        stringRedisTemplate.opsForValue().set("stock:product:" + otherProductId, "200");

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getScanned()).isEqualTo(2);
        assertThat(result.getRepairFailed()).isEqualTo(1);
        assertThat(result.getConsistent()).isEqualTo(1);
        // 异常 Redis 值不被覆盖，MySQL 不被改动
        assertThat(redisStock(productId)).isEqualTo("abc");
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(100);
        // 其他商品不受影响
        assertThat(redisStock(otherProductId)).isEqualTo("200");
    }

    @Test
    void negativeMysqlStockNotAutoFixed() {
        long productId = createProduct("20.00", -5, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "3");

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getRepairFailed()).isEqualTo(1);
        assertThat(result.getRepaired()).isZero();
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(-5);
        assertThat(redisStock(productId)).isEqualTo("3");
    }

    @Test
    void multiProductScanContinuesAfterAnomaly() {
        long consistent = createProduct("20.00", 100, Map.of());
        long higher = createProduct("20.00", 95, Map.of());
        long missing = createProduct("20.00", 50, Map.of());
        long corrupt = createProduct("20.00", 80, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + consistent, "100");
        stringRedisTemplate.opsForValue().set("stock:product:" + higher, "120");
        stringRedisTemplate.delete("stock:product:" + missing);
        stringRedisTemplate.opsForValue().set("stock:product:" + corrupt, "zzz");

        ReconciliationResult result = reconciliationService.scanAndRepair();

        assertThat(result.getScanned()).isEqualTo(4);
        assertThat(result.getConsistent()).isEqualTo(1);
        assertThat(result.getMissing()).isEqualTo(1);
        assertThat(result.getMismatch()).isEqualTo(2);
        assertThat(result.getRepaired()).isEqualTo(2);
        assertThat(result.getRepairFailed()).isEqualTo(1);
        assertThat(redisStock(higher)).isEqualTo("95");
        assertThat(redisStock(missing)).isEqualTo("50");
        assertThat(redisStock(corrupt)).isEqualTo("zzz");
    }

    @Test
    void repeatScanIsIdempotent() {
        long productId = createProduct("20.00", 95, Map.of());
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "100");

        reconciliationService.scanAndRepair();
        ReconciliationResult second = reconciliationService.scanAndRepair();

        assertThat(second.getRepaired()).isZero();
        assertThat(second.getConsistent()).isEqualTo(1);
        assertThat(redisStock(productId)).isEqualTo("95");
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(95);
    }

    @Test
    void concurrentOrderAndReconciliationConverge() throws Exception {
        long userId = 900001L;
        long farmerId = createFarmer();
        long productId = createProductWithSpecs(farmerId, "20.00", 100, "A", "B", "C");
        long addressId = createAddress(userId);
        List<Long> cartIds = List.of(
                addCart(userId, productId, 1, "A"),
                addCart(userId, productId, 1, "B"),
                addCart(userId, productId, 1, "C"));
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, "100");

        int rounds = 3;
        for (int round = 0; round < rounds; round++) {
            int currentRound = round;
            // 制造漂移：Redis 比 MySQL 低 5
            stringRedisTemplate.opsForValue().set("stock:product:" + productId,
                    String.valueOf(productMapper.selectById(productId).getStock() - 5));

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            pool.submit(() -> {
                try {
                    start.await();
                    CreateOrderDTO dto = new CreateOrderDTO();
                    dto.setAddressId(addressId);
                    dto.setSelectedCartIds(List.of(cartIds.get(currentRound)));
                    orderService.createOrder(userId, dto, "tok-" + UUID.randomUUID());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
            });
            pool.submit(() -> {
                try {
                    start.await();
                    reconciliationService.scanAndRepair();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
            });
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            pool.shutdown();

            // 竞态轮内：MySQL 事实绝不被对账覆盖（下单扣减一定生效）
            int mysqlStock = productMapper.selectById(productId).getStock();
            int expectedStock = 100 - currentRound - 1;
            assertThat(mysqlStock).as("第 %s 轮下单应成功扣减且不被对账覆盖", currentRound + 1)
                    .isEqualTo(expectedStock);

            // 竞态轮结束后再执行一轮对账（对应生产环境周期性调度）：此时无在途事务，必然收敛
            reconciliationService.scanAndRepair();
            String redisStock = redisStock(productId);
            assertThat(Integer.parseInt(redisStock)).as("第 %s 轮：对账收敛后 Redis 必须等于 MySQL", currentRound + 1)
                    .isEqualTo(mysqlStock);
        }
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(97);
        assertThat(redisStock(productId)).isEqualTo("97");
    }

    @Test
    void reconciliationTaskDisabledInTestProfile() {
        assertThat(applicationContext.containsBean("inventoryReconciliationTask")).isFalse();
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

    private long createProduct(String price, int stock, Map<String, BigDecimal> attrValues) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("海南芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(1L);
        product.setTitle("海南芒果 " + n);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setUnit("斤");
        product.setLifecycle(3);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);

        attrValues.forEach((value, extraPrice) -> {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(product.getId());
            attribute.setAttrName("规格");
            attribute.setAttrValue(value);
            attribute.setExtraPrice(extraPrice);
            productAttributeMapper.insert(attribute);
        });
        return product.getId();
    }

    private long createProductWithSpecs(long farmerId, String price, int stock, String... specs) {
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

        for (String spec : specs) {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProductId(product.getId());
            attribute.setAttrName("规格");
            attribute.setAttrValue(spec);
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

    private long addCart(long userId, long productId, int quantity, String spec) {
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setSpecSnapshot("[{\"name\":\"规格\",\"value\":\"" + spec + "\"}]");
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        return cart.getId();
    }

    private String redisStock(long productId) {
        return stringRedisTemplate.opsForValue().get("stock:product:" + productId);
    }
}
