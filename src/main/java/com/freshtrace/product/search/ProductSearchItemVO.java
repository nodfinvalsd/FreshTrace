package com.freshtrace.product.search;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品搜索结果项。
 */
@Data
public class ProductSearchItemVO {

    private Long id;

    private String title;

    private String mainImage;

    private BigDecimal price;

    private String unit;

    private Integer salesCount;

    private Double avgRating;

    private Long farmerId;

    private String farmerName;

    private String orchardName;

    private Long categoryId;

    private String categoryName;

    private Integer lifecycle;
}
