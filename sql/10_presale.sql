-- FreshTrace 预售系统 DDL（Phase 7）
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted 逻辑删除
-- V1 定位「预约提醒型预售」：免费预约、不锁价、不付款、不生成订单；成熟后系统通知预约用户，用户再正常下单
-- 因此不设 deposit(定金) 与 presale_price(优惠价) 字段
-- 预约计数：reservation_count 为查询冗余，权威以 COUNT(*) FROM t_presale_reservation 对账刷新
-- 重复预约：UNIQUE(presale_id, user_id) 兜底并发

CREATE TABLE IF NOT EXISTS t_presale (
    id                BIGINT   NOT NULL COMMENT '预售配置ID(雪花)',
    product_id        BIGINT   NOT NULL COMMENT '关联商品ID(1:1)',
    farmer_id         BIGINT   NOT NULL COMMENT '所属果农ID(冗余，归属校验与列表查询)',
    presale_start     DATETIME NOT NULL COMMENT '预售开始时间',
    presale_end       DATETIME NOT NULL COMMENT '预售截止(预计成熟)时间',
    expected_harvest  DATETIME          DEFAULT NULL COMMENT '预计成熟采摘时间',
    max_reservations  INT      NOT NULL DEFAULT 0 COMMENT '最大预约数,0=不限',
    reservation_count INT      NOT NULL DEFAULT 0 COMMENT '当前预约数(冗余,权威以 COUNT(*) 对账)',
    status            TINYINT  NOT NULL DEFAULT 1 COMMENT '0=关闭,1=进行中,2=已结束',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id),
    KEY idx_farmer_id (farmer_id),
    KEY idx_status_end (status, presale_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预售配置表';

CREATE TABLE IF NOT EXISTS t_presale_reservation (
    id          BIGINT   NOT NULL COMMENT '预约ID(雪花)',
    presale_id  BIGINT   NOT NULL COMMENT '预售配置ID',
    user_id     BIGINT   NOT NULL COMMENT '预约用户ID',
    quantity    INT      NOT NULL DEFAULT 1 COMMENT '预约数量',
    notified    TINYINT  NOT NULL DEFAULT 0 COMMENT '成熟后是否已通知 0=否 1=是',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_presale_user (presale_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预售预约表';
