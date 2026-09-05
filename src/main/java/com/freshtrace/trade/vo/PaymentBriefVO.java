package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentBriefVO {

    private String paymentNo;

    private String payMethod;

    private Integer status;

    private String statusDesc;

    private BigDecimal amount;

    private LocalDateTime paidAt;
}
