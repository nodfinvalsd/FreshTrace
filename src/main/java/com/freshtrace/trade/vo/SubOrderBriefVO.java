package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubOrderBriefVO {

    private String subOrderNo;

    private Long farmerId;

    private BigDecimal amount;
}
