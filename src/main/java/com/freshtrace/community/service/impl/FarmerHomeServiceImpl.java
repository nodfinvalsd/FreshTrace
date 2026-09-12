package com.freshtrace.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.community.dto.PostQueryDTO;
import com.freshtrace.community.service.FarmerHomeService;
import com.freshtrace.community.service.PostService;
import com.freshtrace.community.vo.FarmerHomeVO;
import com.freshtrace.community.vo.PostVO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.review.entity.Review;
import com.freshtrace.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 果农主页聚合实现（Phase 6 Day 4）。
 * <p>
 * 缓存策略：读时回写，farmer:home:{farmerId}，TTL 1h；缓存异常不影响业务，仅降级为直查。
 * 失效触发点（各模块写操作后调用 {@link #evict(Long)}）：
 * 评价创建（Phase 4 已接入）、发帖/删帖、商品上架/下架/审核/编辑。
 * 整体缓存不含个性化点赞态，动态预览以匿名身份查询（liked 恒 false）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FarmerHomeServiceImpl implements FarmerHomeService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int PREVIEW_POST_LIMIT = 10;
    private static final int ON_SALE_PRODUCT_LIMIT = 20;

    private final FarmerMapper farmerMapper;
    private final ReviewMapper reviewMapper;
    private final ProductMapper productMapper;
    private final PostService postService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public FarmerHomeVO home(Long farmerId) {
        String key = CacheKeys.farmerHome(farmerId);
        FarmerHomeVO cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        FarmerHomeVO vo = buildHome(farmerId);
        writeCache(key, vo);
        return vo;
    }

    @Override
    public void evict(Long farmerId) {
        try {
            stringRedisTemplate.delete(CacheKeys.farmerHome(farmerId));
        } catch (Exception e) {
            log.warn("evict farmer home cache failed, farmerId={}", farmerId, e);
        }
    }

    private FarmerHomeVO buildHome(Long farmerId) {
        Farmer farmer = farmerMapper.selectById(farmerId);
        if (farmer == null) {
            throw new BizException(ErrorCode.FARMER_NOT_EXISTS);
        }

        FarmerHomeVO vo = new FarmerHomeVO();
        vo.setFarmerId(farmer.getId());
        vo.setRealName(farmer.getRealName());
        vo.setOrchardName(farmer.getOrchardName());
        vo.setOrchardProvince(farmer.getOrchardProvince());
        vo.setOrchardCity(farmer.getOrchardCity());
        vo.setOrchardDistrict(farmer.getOrchardDistrict());
        vo.setOrchardAddress(farmer.getOrchardAddress());
        vo.setOrchardArea(farmer.getOrchardArea());
        vo.setOrchardPhotos(parsePhotos(farmer.getOrchardPhotos()));
        vo.setAvgRating(farmer.getAvgRating());
        vo.setTotalSales(farmer.getTotalSales());
        vo.setReviewCount(Math.toIntExact(reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getFarmerId, farmerId))));
        vo.setProducts(loadOnSaleProducts(farmerId));
        vo.setPosts(loadPreviewPosts(farmerId));
        return vo;
    }

    private List<ProductVO> loadOnSaleProducts(Long farmerId) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getFarmerId, farmerId)
                        .eq(Product::getLifecycle, ProductLifecycle.ON_SALE.getCode())
                        .eq(Product::getAuditStatus, 1)
                        .orderByDesc(Product::getSalesCount)
                        .last("LIMIT " + ON_SALE_PRODUCT_LIMIT))
                .stream()
                .map(this::toProductVO)
                .toList();
    }

    private List<PostVO> loadPreviewPosts(Long farmerId) {
        PostQueryDTO query = new PostQueryDTO();
        query.setPage(1);
        query.setSize(PREVIEW_POST_LIMIT);
        query.setFarmerId(farmerId);
        return postService.page(query, null).getRecords();
    }

    private List<String> parsePhotos(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("farmer orchard photos parse failed", e);
            return List.of();
        }
    }

    private ProductVO toProductVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setSpuId(product.getSpuId());
        vo.setFarmerId(product.getFarmerId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setUnit(product.getUnit());
        vo.setMainImage(product.getMainImage());
        vo.setLifecycle(product.getLifecycle());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setAuditReason(product.getAuditReason());
        vo.setSalesCount(product.getSalesCount());
        vo.setViewCount(product.getViewCount());
        vo.setVersion(product.getVersion());
        return vo;
    }

    private FarmerHomeVO readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, FarmerHomeVO.class);
        } catch (Exception e) {
            log.warn("read farmer home cache failed, key={}", key, e);
            return null;
        }
    }

    private void writeCache(String key, FarmerHomeVO vo) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), CACHE_TTL);
        } catch (Exception e) {
            log.warn("write farmer home cache failed, key={}", key, e);
        }
    }
}
