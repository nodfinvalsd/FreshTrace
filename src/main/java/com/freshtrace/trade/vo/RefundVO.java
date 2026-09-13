package com.freshtrace.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端/果农端退款工单视图。
 */
@Data
public class RefundVO {

    private Long id;

    private String refundNo;

    private Long subOrderId;

    private String subOrderNo;

    private String orderNo;

    private Long orderId;

    private Long farmerId;

    /** 果农真实姓名（t_farmer.real_name） */
    private String farmerName;

    private String orchardName;

    private Long userId;

    /** 买家昵称/用户名 */
    private String buyerName;

    private BigDecimal amount;

    private String reason;

    /** 凭证图片 */
    private List<String> evidenceImages;

    private Integer status;

    private String statusDesc;

    private Long handlerId;

    private String handleReason;

    private LocalDateTime handledAt;

    private LocalDateTime createTime;
}
