package com.freshtrace.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品评价。评价粒度 = 子订单内单个商品，与 t_order_item 一一对应。
 * <p>
 * 业务约束（DDL 已保证）：
 * - UNIQUE(sub_order_id, product_id)：一个子订单内的每个商品最多评价一次；
 * - user_id 为买家（冗余自主订单，业务层校验归属）；
 * - farmer_id 为被评价果农（冗余自子订单，支撑果农主页评价直查）。
 * <p>
 * Phase 4 Day 1 仅建表与持久化层准备，发表/回复业务 Day 3 实现。
 */
@TableName("t_review")
@Data
@EqualsAndHashCode(callSuper = true)
public class Review extends BaseEntity {

    private Long subOrderId;

    private Long productId;

    private Long userId;

    private Long farmerId;

    private Integer rating;

    private String content;

    private String images;

    private String reply;

    private LocalDateTime repliedAt;
}
