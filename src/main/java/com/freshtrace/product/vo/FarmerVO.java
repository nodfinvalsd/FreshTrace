package com.freshtrace.product.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FarmerVO {

    private Long id;

    private Long userId;

    private String realName;

    private String orchardName;

    private String orchardProvince;

    private String orchardCity;

    private String orchardDistrict;

    private BigDecimal avgRating;

    private Integer totalSales;
}
