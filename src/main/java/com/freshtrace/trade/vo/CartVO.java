package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartVO {

    private Long cartId;

    private Long productId;

    private String productTitle;

    private String productImage;

    /** 商品基础单价（不含规格加价），以数据库当前值为准 */
    private BigDecimal price;

    /** 当前可计算单价 = 商品基础价 + 当前规格加价（以 t_product_attribute 当前值为准） */
    private BigDecimal unitPrice;

    /** 展示合计 = unitPrice * quantity，仅用于展示，不作为订单金额 */
    private BigDecimal totalPrice;

    private String specSnapshot;

    private Integer quantity;

    private Boolean selected;

    /** 商品当前库存（商品已失效时为 null） */
    private Integer stock;

    /** 商品是否仍在销售（生命周期=销售中 且 审核通过） */
    private Boolean onSale;

    /** 商品是否存在（逻辑删除后为 false） */
    private Boolean productExists;

    /** 库存是否不足（quantity > stock），仅提示，下单时才做强校验 */
    private Boolean insufficientStock;
}
