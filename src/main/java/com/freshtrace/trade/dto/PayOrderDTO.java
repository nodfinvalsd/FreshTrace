package com.freshtrace.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayOrderDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
