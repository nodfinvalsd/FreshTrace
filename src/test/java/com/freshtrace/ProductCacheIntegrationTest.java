package com.freshtrace;

import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.product.service.ProductHotService;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品详情缓存（Phase 2.14）与热门商品 ZSET（Phase 2.15）集成测试。
 * <p>
 * 依赖本地 MySQL/Redis；test profile 已关闭热门商品定时任务，改由测试显式触发重建。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductHotService productHotService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearCaches() {
        stringRedisTemplate.delete(CacheKeys.hotProducts());
        Set<String> detailKeys = stringRedisTemplate.keys("product:detail:*");
        if (detailKeys != null && !detailKeys.isEmpty()) {
            stringRedisTemplate.delete(detailKeys);
        }
    }

    @Test
    void detailCacheReadWriteAndEvict() {
        Long farmerId = createFarmer();
        long categoryId = createCategory("苹果");
        Long productId = createProduct(categoryId, farmerId, "缓存前标题", new BigDecimal("29.90"), 3, 1);

        String cacheKey = CacheKeys.productDetail(productId);
        assertThat(productService.detail(productId).getTitle()).isEqualTo("缓存前标题");
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();

        Product update = new Product();
        update.setId(productId);
        update.setTitle("缓存后标题");
        productMapper.updateById(update);

        // 缓存命中：直接改库后仍返回旧值
        assertThat(productService.detail(productId).getTitle()).isEqualTo("缓存前标题");

        // 状态变更触发缓存失效：旧缓存删除，下次读回源得到新值
        ProductLifecycleUpdateDTO lifecycle = new ProductLifecycleUpdateDTO();
        lifecycle.setLifecycle(4);
        productService.updateLifecycle(900001L, productId, lifecycle);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();
        assertThat(productService.detail(productId).getTitle()).isEqualTo("缓存后标题");
    }

    @Test
    void hotProductsOrderBySalesAndExcludeNotOnSale() {
        Long farmerId = createFarmer();
        long categoryId = createCategory("柑橘");
        Long low = createProduct(categoryId, farmerId, "低销量柑橘", new BigDecimal("9.90"), 3, 1, 5);
        Long high = createProduct(categoryId, farmerId, "高销量柑橘", new BigDecimal("19.90"), 3, 1, 50);
        Long middle = createProduct(categoryId, farmerId, "中销量柑橘", new BigDecimal("29.90"), 3, 1, 20);
        Long offShelf = createProduct(categoryId, farmerId, "下架爆款", new BigDecimal("99.90"), 0, 1, 999);

        productHotService.refreshHotProducts();

        List<ProductVO> hot = productHotService.hotProducts(10);
        assertThat(hot).extracting(ProductVO::getId)
                .containsExactly(high, middle, low)
                .doesNotContain(offShelf);
    }

    private Long createFarmer() {
        Farmer farmer = new Farmer();
        farmer.setUserId(900001L);
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("云南省");
        farmer.setOrchardCity("昆明市");
        farmer.setOrchardDistrict("盘龙区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return farmer.getId();
    }

    private long createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setSortOrder(0);
        categoryMapper.insert(category);
        return category.getId();
    }

    private Long createProduct(long categoryId, Long farmerId, String title,
                               BigDecimal price, int lifecycle, int auditStatus) {
        return createProduct(categoryId, farmerId, title, price, lifecycle, auditStatus, 0);
    }

    private Long createProduct(long categoryId, Long farmerId, String title,
                               BigDecimal price, int lifecycle, int auditStatus, int salesCount) {
        Spu spu = new Spu();
        spu.setCategoryId(categoryId);
        spu.setName(title);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle(title);
        product.setDescription(title + " 描述");
        product.setPrice(price);
        product.setStock(100);
        product.setUnit("斤");
        product.setMainImage("http://img/" + spu.getId() + ".jpg");
        product.setLifecycle(lifecycle);
        product.setAuditStatus(auditStatus);
        product.setSalesCount(salesCount);
        product.setViewCount(0);
        product.setVersion(0);
        productMapper.insert(product);
        return product.getId();
    }
}
