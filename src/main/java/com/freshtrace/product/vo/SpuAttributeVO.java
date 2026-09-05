package com.freshtrace.product.vo;

import lombok.Data;

import java.util.List;

@Data
public class SpuAttributeVO {

    private Long id;

    private String attrName;

    private List<String> attrValues;

    private Integer sortOrder;
}
