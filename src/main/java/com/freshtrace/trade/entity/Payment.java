package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_payment")
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {

    private String paymentNo;

    private Long orderId;

    private BigDecimal amount;

    private String payMethod;

    private Integer status;

    private LocalDateTime paidAt;
}
