package com.freshtrace.trade.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单详情：主订单 + 子订单 + 商品快照 + 支付 + 地址快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailVO extends OrderVO {

    /** 下单时保存的地址快照（不查当前地址表） */
    private Map<String, String> addressSnapshot;

    private String remark;

    private LocalDateTime finishedAt;

    private PaymentBriefVO payment;
}
