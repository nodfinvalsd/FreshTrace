package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateVO {

    private String orderNo;

    private BigDecimal totalAmount;

    private Integer status;

    private List<SubOrderBriefVO> subOrders;

    /**
     * 幂等命中（同一 Idempotency-Key 重复提交）时仅返回首次成功的订单号，
     * totalAmount/subOrders 为空，客户端应以 orderNo 为准。
     */
    public static OrderCreateVO repeat(String orderNo) {
        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(orderNo);
        return vo;
    }
}
