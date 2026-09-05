package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundResultVO {

    private String refundNo;

    private String subOrderNo;

    /** 退款金额 = SubOrder.amount（来自数据库，不接受客户端金额） */
    private BigDecimal amount;

    /** RefundStatus code（4=已退款） */
    private Integer status;

    private LocalDateTime handledAt;
}
