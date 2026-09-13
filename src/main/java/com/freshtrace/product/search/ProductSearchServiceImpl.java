package com.freshtrace.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ElasticsearchClient elasticsearchClient;
    private final ProductMapper productMapper;
    private final SpuMapper spuMapper;
    private final CategoryMapper categoryMapper;
    private final FarmerMapper farmerMapper;

    @Value("${elasticsearch.index:freshtrace_product}")
    private String index;

    @Override
    public PageVO<ProductSearchItemVO> search(ProductSearchQueryDTO query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), MAX_PAGE_SIZE);

        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("auditStatus").value(1L))));
        filters.add(Query.of(q -> q.term(t -> t.field("lifecycle")
                .value((long) ProductLifecycle.ON_SALE.getCode()))));
        if (query.getCategoryId() != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(query.getCategoryId()))));
        }

        Query keywordQuery = StringUtils.hasText(query.getKeyword())
                ? Query.of(q -> q.multiMatch(mm -> mm.query(query.getKeyword())
                        .fields(List.of("title^3", "spuName^2", "variety^2", "origin^2", "tags", "description"))))
                : Query.of(q -> q.matchAll(m -> m));

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(index)
                .query(q -> q.bool(b -> b.must(keywordQuery).filter(filters)))
                .from((page - 1) * size)
                .size(size);
        List<SortOptions> sorts = buildSorts(query.getSort());
        if (!sorts.isEmpty()) {
            request.sort(sorts);
        }

        try {
            SearchResponse<ProductSearchDoc> response =
                    elasticsearchClient.search(request.build(), ProductSearchDoc.class);
            List<ProductSearchItemVO> records = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::toItemVO)
                    .toList();
            long total = response.hits().total() == null ? records.size() : response.hits().total().value();
            PageVO<ProductSearchItemVO> vo = new PageVO<>();
            vo.setRecords(records);
            vo.setTotal(total);
            vo.setPage(page);
            vo.setSize(size);
            return vo;
        } catch (IOException e) {
            log.error("product search failed, query={}", query, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "商品搜索失败");
        }
    }

    @Override
    public void indexProduct(Long productId) {
        if (productId == null) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null || product.getAuditStatus() == null || product.getAuditStatus() != 1) {
            deleteProduct(productId);
            return;
        }
        ProductSearchDoc doc = toDoc(product);
        try {
            elasticsearchClient.index(i -> i.index(index).id(String.valueOf(productId)).document(doc));
        } catch (IOException e) {
            throw new IllegalStateException("ES 商品索引写入失败: productId=" + productId, e);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            elasticsearchClient.delete(d -> d.index(index).id(String.valueOf(productId)));
        } catch (Exception e) {
            log.warn("ES 删除商品索引失败, productId={}", productId, e);
        }
    }

    private List<SortOptions> buildSorts(String sort) {
        if (sort == null) {
            return List.of();
        }
        return switch (sort) {
            case "price_asc" -> List.of(fieldSort("price", SortOrder.Asc));
            case "price_desc" -> List.of(fieldSort("price", SortOrder.Desc));
            case "sales" -> List.of(fieldSort("salesCount", SortOrder.Desc));
            case "rating" -> List.of(fieldSort("avgRating", SortOrder.Desc));
            default -> List.of();
        };
    }

    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
    }

    private ProductSearchDoc toDoc(Product product) {
        Spu spu = product.getSpuId() == null ? null : spuMapper.selectById(product.getSpuId());
        Category category = spu == null || spu.getCategoryId() == null
                ? null : categoryMapper.selectById(spu.getCategoryId());
        Farmer farmer = product.getFarmerId() == null ? null : farmerMapper.selectById(product.getFarmerId());

        ProductSearchDoc doc = new ProductSearchDoc();
        doc.setId(product.getId());
        doc.setTitle(product.getTitle());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setStock(product.getStock());
        doc.setUnit(product.getUnit());
        doc.setMainImage(product.getMainImage());
        doc.setLifecycle(product.getLifecycle());
        doc.setAuditStatus(product.getAuditStatus());
        doc.setSalesCount(product.getSalesCount());
        doc.setSpuName(spu == null ? null : spu.getName());
        doc.setVariety(spu == null ? null : spu.getVariety());
        doc.setOrigin(spu == null ? null : spu.getOrigin());
        doc.setTags(spu == null ? null : spu.getTags());
        doc.setCategoryId(spu == null ? null : spu.getCategoryId());
        doc.setCategoryName(category == null ? null : category.getName());
        doc.setFarmerId(product.getFarmerId());
        doc.setFarmerName(farmer == null ? null : farmer.getRealName());
        doc.setOrchardName(farmer == null ? null : farmer.getOrchardName());
        doc.setAvgRating(farmer == null || farmer.getAvgRating() == null
                ? null : farmer.getAvgRating().doubleValue());
        doc.setCreateTime(product.getCreateTime() == null
                ? null : product.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return doc;
    }

    private ProductSearchItemVO toItemVO(ProductSearchDoc doc) {
        ProductSearchItemVO vo = new ProductSearchItemVO();
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setMainImage(doc.getMainImage());
        vo.setPrice(doc.getPrice());
        vo.setUnit(doc.getUnit());
        vo.setSalesCount(doc.getSalesCount());
        vo.setAvgRating(doc.getAvgRating());
        vo.setFarmerId(doc.getFarmerId());
        vo.setFarmerName(doc.getFarmerName());
        vo.setOrchardName(doc.getOrchardName());
        vo.setCategoryId(doc.getCategoryId());
        vo.setCategoryName(doc.getCategoryName());
        vo.setLifecycle(doc.getLifecycle());
        return vo;
    }
}
