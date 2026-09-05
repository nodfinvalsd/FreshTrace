package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_refund")
@Data
@EqualsAndHashCode(callSuper = true)
public class Refund extends BaseEntity {

    private String refundNo;

    private Long subOrderId;

    private Long farmerId;

    private Long userId;

    private BigDecimal amount;

    private String reason;

    private String evidenceImages;

    private Integer status;

    private Long handlerId;

    private String handleReason;

    private LocalDateTime handledAt;
}
