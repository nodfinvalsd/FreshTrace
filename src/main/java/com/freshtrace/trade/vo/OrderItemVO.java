package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单商品明细展示：全部来自 OrderItem 快照，不查询当前商品数据。
 */
@Data
public class OrderItemVO {

    private Long productId;

    private String productTitle;

    private String productImage;

    private String specSnapshot;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalPrice;
}
