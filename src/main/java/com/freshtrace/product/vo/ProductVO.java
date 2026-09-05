package com.freshtrace.product.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVO {

    private Long id;

    private Long spuId;

    private Long farmerId;

    private String title;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private String unit;

    private String mainImage;

    private Integer lifecycle;

    private Integer auditStatus;

    private String auditReason;

    private Integer salesCount;

    private Integer viewCount;

    private Integer version;
}
