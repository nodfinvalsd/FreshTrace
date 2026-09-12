package com.freshtrace.presale.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预售配置视图。附带商品基础快照，便于果农管理列表与用户端展示。
 */
@Data
public class PresaleVO {

    private Long id;

    private Long productId;

    private Long farmerId;

    private LocalDateTime presaleStart;

    private LocalDateTime presaleEnd;

    private LocalDateTime expectedHarvest;

    private Integer maxReservations;

    private Integer reservationCount;

    private Integer status;

    private String statusDesc;

    private String productTitle;

    private String productImage;

    private BigDecimal productPrice;

    private Integer productLifecycle;
}
