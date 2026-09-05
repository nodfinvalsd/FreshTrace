package com.freshtrace.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spu_attribute")
public class SpuAttribute extends BaseEntity {

    private Long spuId;

    private String attrName;

    private String attrValues;

    private Integer sortOrder;
}
