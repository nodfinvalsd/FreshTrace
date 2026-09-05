package com.freshtrace.product.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductAttributeVO {

    private Long id;

    private String attrName;

    private String attrValue;

    private BigDecimal extraPrice;
}
