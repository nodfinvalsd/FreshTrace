package com.freshtrace.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CancelOrderVO {

    private String orderNo;

    /** OrderStatus code（5=已取消） */
    private Integer status;

    private LocalDateTime cancelledAt;
}
