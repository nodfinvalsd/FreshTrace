-- FreshTrace 评价中心 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted 逻辑删除
-- 评价粒度 = 子订单内单个商品，与 t_order_item 一一对应；UNIQUE(sub_order_id, product_id) 兜底并发重复评价

CREATE TABLE IF NOT EXISTS t_review (
    id           BIGINT        NOT NULL COMMENT '评价ID(雪花)',
    sub_order_id BIGINT        NOT NULL COMMENT '关联子订单ID',
    product_id   BIGINT        NOT NULL COMMENT '被评价商品ID',
    user_id      BIGINT        NOT NULL COMMENT '评价人ID(买家)',
    farmer_id    BIGINT        NOT NULL COMMENT '被评价果农ID',
    rating       TINYINT       NOT NULL COMMENT '评分(1-5)',
    content      VARCHAR(1000)          DEFAULT NULL COMMENT '文字评价',
    images       VARCHAR(2000)          DEFAULT NULL COMMENT '图片URL列表(JSON)',
    reply        VARCHAR(500)           DEFAULT NULL COMMENT '果农回复',
    replied_at   DATETIME               DEFAULT NULL COMMENT '回复时间',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_order_product (sub_order_id, product_id),
    KEY idx_product_id (product_id),
    KEY idx_farmer_id (farmer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';
