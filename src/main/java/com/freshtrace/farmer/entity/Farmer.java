package com.freshtrace.farmer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farmer")
public class Farmer extends BaseEntity {

    private Long userId;

    private String realName;

    private String idCard;

    private String orchardName;

    private String orchardProvince;

    private String orchardCity;

    private String orchardDistrict;

    private String orchardAddress;

    private BigDecimal orchardArea;

    private String categoryIds;

    private String orchardPhotos;

    private String certificationImage;

    private Integer auditStatus;

    private String auditReason;

    private LocalDateTime auditedAt;

    private Long auditedBy;

    private BigDecimal avgRating;

    private Integer totalSales;
}
