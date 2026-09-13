package com.freshtrace.product.service;

import com.freshtrace.product.vo.ProductVO;

import java.util.List;

/**
 * 热门商品服务（Phase 2.15）。
 */
public interface ProductHotService {

    /**
     * 按销量重建热门商品 ZSET（Top100）。
     */
    void refreshHotProducts();

    /**
     * 读取热门商品列表（按销量倒序）。
     */
    List<ProductVO> hotProducts(int limit);
}
