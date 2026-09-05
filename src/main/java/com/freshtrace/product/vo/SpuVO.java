package com.freshtrace.product.vo;

import lombok.Data;

import java.util.List;

@Data
public class SpuVO {

    private Long id;

    private Long categoryId;

    private String name;

    private String variety;

    private String origin;

    private String description;

    private String mainImage;

    private List<String> tags;

    private Integer status;

    private List<SpuAttributeVO> attributes;
}
