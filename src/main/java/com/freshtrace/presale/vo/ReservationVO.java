package com.freshtrace.presale.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的预约视图。
 */
@Data
public class ReservationVO {

    private Long id;

    private Long presaleId;

    private Long productId;

    private String productTitle;

    private String productImage;

    private BigDecimal productPrice;

    private LocalDateTime presaleEnd;

    private LocalDateTime expectedHarvest;

    private Integer quantity;

    private Integer notified;

    private Integer presaleStatus;

    private String presaleStatusDesc;

    /** 预约时间 */
    private LocalDateTime createTime;
}
