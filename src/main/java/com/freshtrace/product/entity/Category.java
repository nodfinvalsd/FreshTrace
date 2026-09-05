package com.freshtrace.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class Category extends BaseEntity {

    private String name;

    private String iconUrl;

    private Integer sortOrder;
}
