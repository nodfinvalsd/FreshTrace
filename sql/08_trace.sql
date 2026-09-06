-- FreshTrace 溯源中心 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted 逻辑删除
-- 溯源节点为商品生命周期事件记录：occurred_at(业务事件发生日期) 与 create_time(录入时间) 分离，保留审计语义
-- 索引 (product_id, occurred_at) 服务商品溯源时间线公开查询 WHERE product_id=? ORDER BY occurred_at ASC

CREATE TABLE IF NOT EXISTS t_trace_node (
    id          BIGINT        NOT NULL COMMENT '溯源节点ID(雪花)',
    product_id  BIGINT        NOT NULL COMMENT '关联商品ID',
    node_type   TINYINT       NOT NULL COMMENT '节点类型 1=播种,2=施肥,3=开花,4=套袋,5=成熟,6=采摘,7=发货',
    title       VARCHAR(100)  NOT NULL COMMENT '节点标题',
    description TEXT                   DEFAULT NULL COMMENT '节点描述',
    images      VARCHAR(2000)          DEFAULT NULL COMMENT '图片URL列表(JSON)',
    occurred_at DATE          NOT NULL COMMENT '业务事件实际发生日期',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_product_id_occurred (product_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='溯源节点表';
