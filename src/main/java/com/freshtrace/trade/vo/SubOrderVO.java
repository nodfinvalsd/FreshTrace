package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SubOrderVO {

    private String subOrderNo;

    private Long farmerId;

    private Integer status;

    private String statusDesc;

    private BigDecimal amount;

    /** 退款记录，未退款时为 null（合法业务状态） */
    private RefundBriefVO refund;

    private List<OrderItemVO> items;
}
