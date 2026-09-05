package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundBriefVO {

    private String refundNo;

    private Integer status;

    private String statusDesc;

    private BigDecimal amount;

    private String reason;

    private LocalDateTime createTime;
}
