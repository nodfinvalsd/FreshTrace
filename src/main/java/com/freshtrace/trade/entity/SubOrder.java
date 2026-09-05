package com.freshtrace.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("t_sub_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubOrder extends BaseEntity {

    private String subOrderNo;

    private Long orderId;

    private Long farmerId;

    private BigDecimal amount;

    private Integer status;

    private String logisticsCompany;

    private String logisticsNo;

    private LocalDateTime shippedAt;

    private LocalDateTime receivedAt;

    @Version
    private Integer version;
}
