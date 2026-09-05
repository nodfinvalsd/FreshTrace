package com.freshtrace.product.vo;

import lombok.Data;

@Data
public class CategoryVO {

    private Long id;

    private String name;

    private String iconUrl;

    private Integer sortOrder;
}
