package com.freshtrace;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.freshtrace.common.PageVO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.product.search.ProductSearchItemVO;
import com.freshtrace.product.search.ProductSearchQueryDTO;
import com.freshtrace.product.search.ProductSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品 ES 搜索集成测试（依赖本地 docker-compose 的 Elasticsearch，含 IK 插件）。
 * <p>
 * test profile 默认 {@code elasticsearch.enabled=false}，本测试通过 @TestPropertySource 打开，
 * 覆盖「索引 mapping 初始化 → 商品索引 → 关键词检索 → 分类筛选 → 排序」全链路。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"elasticsearch.enabled=true"})
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductSearchIntegrationTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ProductSearchService productSearchService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Value("${elasticsearch.index:freshtrace_product}")
    private String index;

    @BeforeEach
    void clearIndex() throws Exception {
        elasticsearchClient.deleteByQuery(d -> d.index(index).query(q -> q.matchAll(m -> m)));
        refreshIndex();
    }

    @Test
    void searchByKeywordWithIkAnalyzer() throws Exception {
        Long farmerId = createFarmer();
        long categoryId = createCategory("苹果");
        Long appleId = createProduct(categoryId, farmerId, "云南冰糖心丑苹果", new BigDecimal("29.90"), 3, 1);
        Long mangoId = createProduct(categoryId, farmerId, "海南贵妃芒果", new BigDecimal("39.90"), 3, 1);
        indexAndRefresh(appleId, mangoId);

        PageVO<ProductSearchItemVO> page = productSearchService.search(query("苹果", null, null));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getId()).isEqualTo(appleId);
        assertThat(page.getRecords().get(0).getCategoryName()).isEqualTo("苹果");
        assertThat(page.getRecords().get(0).getFarmerName()).isEqualTo("张三");
    }

    @Test
    void searchFilterByCategory() throws Exception {
        Long farmerId = createFarmer();
        long appleCategory = createCategory("苹果");
        long mangoCategory = createCategory("芒果");
        Long appleId = createProduct(appleCategory, farmerId, "烟台红富士苹果", new BigDecimal("19.90"), 3, 1);
        Long mangoId = createProduct(mangoCategory, farmerId, "广西百色芒果", new BigDecimal("25.90"), 3, 1);
        indexAndRefresh(appleId, mangoId);

        PageVO<ProductSearchItemVO> page = productSearchService.search(query(null, mangoCategory, null));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getId()).isEqualTo(mangoId);
    }

    @Test
    void searchSortByPriceAsc() throws Exception {
        Long farmerId = createFarmer();
        long categoryId = createCategory("柑橘");
        Long cheap = createProduct(categoryId, farmerId, "砂糖橘小果", new BigDecimal("9.90"), 3, 1);
        Long expensive = createProduct(categoryId, farmerId, "沃柑大果", new BigDecimal("49.90"), 3, 1);
        indexAndRefresh(cheap, expensive);

        PageVO<ProductSearchItemVO> page = productSearchService.search(query(null, categoryId, "price_asc"));

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(ProductSearchItemVO::getId)
                .containsExactly(cheap, expensive);
    }

    @Test
    void searchExcludesUnauditedAndNotOnSale() throws Exception {
        Long farmerId = createFarmer();
        long categoryId = createCategory("葡萄");
        Long onSale = createProduct(categoryId, farmerId, "阳光玫瑰葡萄", new BigDecimal("59.90"), 3, 1);
        Long planting = createProduct(categoryId, farmerId, "巨峰葡萄", new BigDecimal("15.90"), 0, 1);
        Long pendingAudit = createProduct(categoryId, farmerId, "夏黑葡萄", new BigDecimal("12.90"), 3, 0);
        indexAndRefresh(onSale, planting, pendingAudit);

        PageVO<ProductSearchItemVO> page = productSearchService.search(query("葡萄", null, null));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getId()).isEqualTo(onSale);
    }

    @Test
    void offShelfProductExcludedFromSearch() throws Exception {
        Long farmerId = createFarmer();
        long categoryId = createCategory("香蕉");
        Long productId = createProduct(categoryId, farmerId, "高山香蕉", new BigDecimal("8.80"), 3, 1);
        indexAndRefresh(productId);
        assertThat(productSearchService.search(query("香蕉", null, null)).getTotal()).isEqualTo(1);

        Product update = new Product();
        update.setId(productId);
        update.setLifecycle(4);
        productMapper.updateById(update);
        productSearchService.indexProduct(productId);
        refreshIndex();

        assertThat(productSearchService.search(query("香蕉", null, null)).getTotal()).isZero();
    }

    private ProductSearchQueryDTO query(String keyword, Long categoryId, String sort) {
        ProductSearchQueryDTO dto = new ProductSearchQueryDTO();
        dto.setKeyword(keyword);
        dto.setCategoryId(categoryId);
        dto.setSort(sort);
        dto.setPage(1);
        dto.setSize(10);
        return dto;
    }

    private void indexAndRefresh(Long... productIds) throws Exception {
        for (Long productId : productIds) {
            productSearchService.indexProduct(productId);
        }
        refreshIndex();
    }

    private void refreshIndex() throws Exception {
        elasticsearchClient.indices().refresh(r -> r.index(index));
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
        Spu spu = new Spu();
        spu.setCategoryId(categoryId);
        spu.setName(title);
        spu.setVariety("品种A");
        spu.setOrigin("云南");
        spu.setTags("[\"有机\",\"现摘\"]");
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
        product.setSalesCount(0);
        product.setViewCount(0);
        product.setVersion(0);
        productMapper.insert(product);
        return product.getId();
    }
}
