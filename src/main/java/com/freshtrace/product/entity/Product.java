package com.freshtrace.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product")
public class Product extends BaseEntity {

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

    @Version
    private Integer version;
}
