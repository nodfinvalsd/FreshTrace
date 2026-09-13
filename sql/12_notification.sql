-- FreshTrace 消息通知 DDL（Phase 9）
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted
-- 通知来源：各业务模块将事件发到 MQ，通知模块消费者统一落库，业务模块不直接依赖通知模块（解耦）
-- dedup_key：幂等去重键（tag:relatedId:userId），UNIQUE 兜底 MQ 重复投递，避免同一事件写入多条通知
-- 说明：本表按 v1.1 确定的 BaseEntity 规范实现，覆盖数据库设计文档中旧的 AUTO_INCREMENT + created_at 版本

CREATE TABLE IF NOT EXISTS t_notification (
    id          BIGINT       NOT NULL COMMENT '通知ID(雪花)',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID',
    type        TINYINT      NOT NULL COMMENT '1=订单,2=预售,3=聊天,4=系统',
    title       VARCHAR(200) NOT NULL COMMENT '通知标题',
    content     VARCHAR(500)          DEFAULT NULL COMMENT '通知内容',
    related_id  BIGINT                DEFAULT NULL COMMENT '关联业务ID(子订单/预售/评价等)',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读 0=否 1=是',
    dedup_key   VARCHAR(200)          DEFAULT NULL COMMENT '幂等去重键 tag:relatedId:userId',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedup_key (dedup_key),
    KEY idx_user_id_read (user_id, is_read),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';
