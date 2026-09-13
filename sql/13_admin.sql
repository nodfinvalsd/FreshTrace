-- FreshTrace 管理后台 DDL（Phase 10）
-- 约定：
--   t_report        遵循 BaseEntity 规范（id 雪花ID + create_time/update_time/deleted）
--   t_operation_log 追加型审计表，只增不改不删，故不设 update_time/deleted（见设计决策③）
-- 说明：t_refund 已在 06_trade.sql 建表，Phase 10 仅新增以下两张表

-- 举报表
CREATE TABLE IF NOT EXISTS t_report (
    id              BIGINT        NOT NULL COMMENT '举报ID(雪花)',
    user_id         BIGINT        NOT NULL COMMENT '举报人',
    target_type     TINYINT       NOT NULL COMMENT '1=溯源信息,2=商品品质,3=果农行为',
    target_id       BIGINT        NOT NULL COMMENT '举报对象ID',
    reason          VARCHAR(500)  NOT NULL COMMENT '举报原因',
    evidence_images VARCHAR(2000)          DEFAULT NULL COMMENT '凭证图片(JSON)',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待处理,1=已处理(有效),2=已驳回(无效)',
    handler_id      BIGINT                 DEFAULT NULL COMMENT '处理人ID(管理员)',
    handle_reason   VARCHAR(500)           DEFAULT NULL COMMENT '处理意见',
    handled_at      DATETIME               DEFAULT NULL COMMENT '处理时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_target (target_type, target_id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报表';

-- 操作日志表（追加型审计）
CREATE TABLE IF NOT EXISTS t_operation_log (
    id          BIGINT        NOT NULL COMMENT '日志ID(雪花)',
    operator_id BIGINT                 DEFAULT NULL COMMENT '操作人ID',
    target_type VARCHAR(50)   NOT NULL COMMENT '操作对象类型(FARMER/PRODUCT/REFUND/REPORT...)',
    target_id   BIGINT                 DEFAULT NULL COMMENT '操作对象ID',
    action      VARCHAR(50)   NOT NULL COMMENT '操作动作',
    detail      VARCHAR(1000)          DEFAULT NULL COMMENT '参数摘要(JSON)',
    ip_address  VARCHAR(50)            DEFAULT NULL COMMENT '操作IP',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_target (target_type, target_id),
    KEY idx_operator (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
