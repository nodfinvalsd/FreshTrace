-- FreshTrace 即时通讯 DDL（Phase 8）
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted
-- 会话：买家与果农一对一，UNIQUE(user_id, farmer_id) 兜底并发重复创建；product_id 记录来源商品(可空)
-- 消息：sender_id 为登录账号 t_user.id(果农即其 user_id)，sender_role 由服务端根据会话成员关系推导，不信任客户端

CREATE TABLE IF NOT EXISTS t_conversation (
    id              BIGINT       NOT NULL COMMENT '会话ID(雪花)',
    user_id         BIGINT       NOT NULL COMMENT '买家用户ID',
    farmer_id       BIGINT       NOT NULL COMMENT '果农ID(t_farmer.id)',
    product_id      BIGINT                DEFAULT NULL COMMENT '来源商品ID(可空)',
    last_message    VARCHAR(500)          DEFAULT NULL COMMENT '最后一条消息摘要',
    last_message_at DATETIME              DEFAULT NULL COMMENT '最后消息时间',
    unread_user     INT          NOT NULL DEFAULT 0 COMMENT '买家未读数',
    unread_farmer   INT          NOT NULL DEFAULT 0 COMMENT '果农未读数',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_farmer (user_id, farmer_id),
    KEY idx_farmer_id (farmer_id),
    KEY idx_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

CREATE TABLE IF NOT EXISTS t_chat_message (
    id              BIGINT        NOT NULL COMMENT '消息ID(雪花)',
    conversation_id BIGINT        NOT NULL COMMENT '会话ID',
    sender_id       BIGINT        NOT NULL COMMENT '发送者登录账号 user_id',
    sender_role     TINYINT       NOT NULL COMMENT '0=买家,1=果农',
    content         VARCHAR(1000) NOT NULL COMMENT '消息内容',
    is_read         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读 0=否 1=是',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_conv_id (conversation_id, id),
    KEY idx_conv_read (conversation_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
