package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单商品明细：下单后不可变，仅记录成交快照。
 * 从表无独立业务生命周期，不引入逻辑删除（与 t_product_attribute / t_product_image 约定一致）。
 */
@TableName("t_order_item")
@Data
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long subOrderId;

    private Long productId;

    private String productTitle;

    private String productImage;

    private String specSnapshot;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalPrice;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
