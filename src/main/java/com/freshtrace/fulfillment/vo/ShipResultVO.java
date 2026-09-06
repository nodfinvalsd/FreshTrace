package com.freshtrace.fulfillment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发货结果。发货成功后子订单状态为待收货。
 */
@Data
public class ShipResultVO {

    private String subOrderNo;

    private Integer status;

    private String statusDesc;

    private String logisticsCompany;

    private String logisticsNo;

    private LocalDateTime shippedAt;
}
