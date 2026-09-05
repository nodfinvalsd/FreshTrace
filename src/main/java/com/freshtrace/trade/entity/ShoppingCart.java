package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("t_shopping_cart")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShoppingCart extends BaseEntity {

    private Long userId;

    private Long productId;

    private String specSnapshot;

    private Integer quantity;

    private Integer selected;
}
