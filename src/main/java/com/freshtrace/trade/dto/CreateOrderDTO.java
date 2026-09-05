package com.freshtrace.trade.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDTO {

    @NotNull(message = "收货地址不能为空")
    @Positive(message = "地址ID非法")
    private Long addressId;

    @NotEmpty(message = "请选择要结算的商品")
    @Size(max = 50, message = "单次结算商品数量过多")
    private List<@NotNull(message = "购物车项ID不能为空") Long> selectedCartIds;

    @Size(max = 500, message = "备注过长")
    private String remark;
}
