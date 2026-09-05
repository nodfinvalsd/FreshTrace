package com.freshtrace.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spu")
public class Spu extends BaseEntity {

    private Long categoryId;

    private String name;

    private String variety;

    private String origin;

    private String description;

    private String mainImage;

    private String tags;

    private Integer status;
}
