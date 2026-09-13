package com.freshtrace.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.service.ProductHotService;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 热门商品服务实现：Redis ZSET（member=productId，score=销量）缓存 Top100。
 * <p>
 * ZSET 由定时任务重建；读取时若缓存为空则懒重建兜底（如任务关闭或首次启动）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductHotServiceImpl implements ProductHotService {

    private static final int TOP_N = 100;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ProductMapper productMapper;
    private final ProductService productService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void refreshHotProducts() {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getAuditStatus, 1)
                .eq(Product::getLifecycle, ProductLifecycle.ON_SALE.getCode())
                .orderByDesc(Product::getSalesCount)
                .orderByDesc(Product::getId)
                .last("LIMIT " + TOP_N));

        String key = CacheKeys.hotProducts();
        stringRedisTemplate.delete(key);
        for (Product product : products) {
            double score = product.getSalesCount() == null ? 0D : product.getSalesCount();
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(product.getId()), score);
        }
        log.info("hot products cache refreshed, size={}", products.size());
    }

    @Override
    public List<ProductVO> hotProducts(int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String key = CacheKeys.hotProducts();
        List<Long> productIds = readTopIds(key, size);
        if (productIds.isEmpty()) {
            refreshHotProducts();
            productIds = readTopIds(key, size);
        }
        if (productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductVO> byId = productService.batchBrief(productIds).stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
        return productIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Long> readTopIds(String key, int size) {
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(key, 0, size - 1L);
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        return ids.stream().map(Long::valueOf).toList();
    }
}
