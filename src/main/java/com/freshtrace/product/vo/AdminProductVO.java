package com.freshtrace.product.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端商品审核列表视图：在商品基础字段上补充果农/SPU/品类名称，便于审核。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminProductVO extends ProductVO {

    private String farmerName;

    private String orchardName;

    private String spuName;

    private String categoryName;
}
