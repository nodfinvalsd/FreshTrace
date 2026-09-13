package com.freshtrace.product.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品搜索文档（ES 索引模型）。
 * <p>
 * 冗余 SPU / 品类 / 果农 展示字段，避免搜索命中后再回查 MySQL（N+1）。
 * 价格与销量仅用于搜索排序，商品详情页仍以 MySQL 实时数据为准。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSearchDoc {

    private Long id;

    private String title;

    private String description;

    private String spuName;

    private String variety;

    private String origin;

    private String tags;

    private Long categoryId;

    private String categoryName;

    private Long farmerId;

    private String farmerName;

    private String orchardName;

    private Double avgRating;

    private BigDecimal price;

    private Integer stock;

    private Integer salesCount;

    private Integer lifecycle;

    private Integer auditStatus;

    private String mainImage;

    private String unit;

    /** 创建时间（epoch millis），用于后续新品排序 */
    private Long createTime;
}
