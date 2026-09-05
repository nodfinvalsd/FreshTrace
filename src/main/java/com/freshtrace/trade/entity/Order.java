package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private Integer status;

    private String addressSnapshot;

    private String remark;

    private LocalDateTime paidAt;

    private LocalDateTime finishedAt;

    private LocalDateTime cancelledAt;

    @Version
    private Integer version;
}
