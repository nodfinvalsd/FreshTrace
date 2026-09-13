package com.freshtrace.product.search;

import lombok.Data;

/**
 * 商品搜索查询条件。
 */
@Data
public class ProductSearchQueryDTO {

    /** 关键词：匹配名称/品种/产地/标签/描述/果园 */
    private String keyword;

    private Long categoryId;

    /** 排序：default / price_asc / price_desc / sales / rating */
    private String sort;

    private Integer page = 1;

    private Integer size = 10;
}
