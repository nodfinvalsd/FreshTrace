package com.freshtrace.presale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预售配置。商品与预售配置 1:1（UNIQUE(product_id)）。
 * <p>
 * V1 定位「预约提醒型预售」：免费预约、不锁价、不付款、不生成订单，
 * 因此不设 deposit 与 presale_price；成熟（presale_end 到期）后系统通知预约用户，用户再正常下单。
 */
@TableName("t_presale")
@Data
@EqualsAndHashCode(callSuper = true)
public class Presale extends BaseEntity {

    private Long productId;

    private Long farmerId;

    private LocalDateTime presaleStart;

    private LocalDateTime presaleEnd;

    private LocalDateTime expectedHarvest;

    /** 最大预约数，0=不限 */
    private Integer maxReservations;

    /** 当前预约数（冗余，权威以 COUNT(*) 对账刷新） */
    private Integer reservationCount;

    private Integer status;
}
