package com.freshtrace.fulfillment.vo;

import com.freshtrace.trade.vo.OrderItemVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 果农订单管理页的子订单展示。
 * <p>
 * 商品明细复用 {@link OrderItemVO}（全部来自 t_order_item 快照）。
 * 不返回买家姓名/手机号等隐私信息，仅暴露履约所需字段。
 */
@Data
public class FarmerSubOrderVO {

    private String subOrderNo;

    private String orderNo;

    private Integer status;

    private String statusDesc;

    private BigDecimal amount;

    private String logisticsCompany;

    private String logisticsNo;

    private LocalDateTime shippedAt;

    private LocalDateTime receivedAt;

    private LocalDateTime createTime;

    private List<OrderItemVO> items;
}
