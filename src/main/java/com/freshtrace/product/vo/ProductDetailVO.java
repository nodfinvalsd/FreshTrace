package com.freshtrace.product.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDetailVO extends ProductVO {

    private SpuVO spu;

    private CategoryVO category;

    private FarmerVO farmer;

    private List<ProductAttributeVO> attributes;

    private List<ProductImageVO> images;

    private Integer reviewCount;

    private Integer traceNodeCount;
}
