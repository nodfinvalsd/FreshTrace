package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private String orderNo;

    private Integer status;

    private String statusDesc;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private LocalDateTime createTime;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    private List<SubOrderVO> subOrders;
}
