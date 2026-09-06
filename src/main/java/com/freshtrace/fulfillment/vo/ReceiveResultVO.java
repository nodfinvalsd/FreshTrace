package com.freshtrace.fulfillment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 确认收货结果。确认成功后子订单状态为已完成。
 */
@Data
public class ReceiveResultVO {

    private String subOrderNo;

    private Integer status;

    private String statusDesc;

    private LocalDateTime receivedAt;
}
