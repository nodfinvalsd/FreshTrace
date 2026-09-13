package com.freshtrace.product.search;

import com.freshtrace.common.PageVO;

/**
 * 商品搜索服务：ES 索引读写与检索。
 */
public interface ProductSearchService {

    /**
     * 多字段检索 + 分类筛选 + 排序（仅返回审核通过且在售商品）。
     */
    PageVO<ProductSearchItemVO> search(ProductSearchQueryDTO query);

    /**
     * 将商品写入 / 更新 ES 索引；商品不存在或未审核通过时删除索引文档。
     */
    void indexProduct(Long productId);

    /**
     * 删除商品索引文档。
     */
    void deleteProduct(Long productId);
}
