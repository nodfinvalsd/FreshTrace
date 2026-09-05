package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResultVO {

    private String orderNo;

    /** 支付金额 = Order.payAmount（来自数据库，绝不使用客户端金额） */
    private BigDecimal payAmount;

    /** PaymentStatus code（1=成功） */
    private Integer status;

    private LocalDateTime paidAt;
}
