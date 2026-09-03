-- FreshTrace 果农认证模块 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，create_time/update_time/deleted 为通用字段
-- 一个用户只能存在一条果农认证记录(uk_user_id 唯一)

CREATE TABLE IF NOT EXISTS t_farmer (
    id                 BIGINT        NOT NULL COMMENT '果农ID(雪花)',
    user_id            BIGINT        NOT NULL COMMENT '关联用户ID',
    real_name          VARCHAR(50)   NOT NULL COMMENT '真实姓名',
    id_card            VARCHAR(255)  NOT NULL COMMENT '身份证号(AES加密)',
    orchard_name       VARCHAR(100)  NOT NULL COMMENT '果园名称',
    orchard_province   VARCHAR(30)   NOT NULL COMMENT '果园所在省',
    orchard_city       VARCHAR(30)   NOT NULL COMMENT '果园所在市',
    orchard_district   VARCHAR(30)   NOT NULL COMMENT '果园所在区',
    orchard_address    VARCHAR(255)  NOT NULL COMMENT '果园详细地址',
    orchard_area       DECIMAL(10,2)          DEFAULT NULL COMMENT '果园面积(亩)',
    category_ids       VARCHAR(500)           DEFAULT NULL COMMENT '种植品类ID列表(JSON)',
    orchard_photos     VARCHAR(2000)          DEFAULT NULL COMMENT '果园照片URL列表(JSON)',
    certification_image VARCHAR(500)          DEFAULT NULL COMMENT '相关证书图片URL',
    audit_status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待审核, 1=通过, 2=驳回',
    audit_reason       VARCHAR(500)           DEFAULT NULL COMMENT '审核意见',
    audited_at         DATETIME               DEFAULT NULL COMMENT '审核时间',
    audited_by         BIGINT                 DEFAULT NULL COMMENT '审核人ID',
    avg_rating         DECIMAL(2,1)           DEFAULT 5.0 COMMENT '平均评分',
    total_sales        INT                    DEFAULT 0 COMMENT '总销量',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_audit_status (audit_status),
    KEY idx_orchard_city (orchard_city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='果农认证表';
