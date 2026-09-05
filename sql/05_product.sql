-- FreshTrace 商品中心 - 商品 SKU 模块 DDL
-- 约定：id 雪花ID(应用层生成)，主表 t_product 使用 create_time/update_time/deleted 通用字段 + version 乐观锁
-- 从表 t_product_attribute / t_product_image 采用物理删除（全量替换策略 + UNIQUE 约束，不引入逻辑删除避免冲突）

CREATE TABLE IF NOT EXISTS t_product (
    id           BIGINT        NOT NULL COMMENT '商品ID(雪花)',
    spu_id       BIGINT        NOT NULL COMMENT '关联SPU ID',
    farmer_id    BIGINT        NOT NULL COMMENT '关联果农ID',
    title        VARCHAR(200)  NOT NULL COMMENT '商品标题',
    description  TEXT                  DEFAULT NULL COMMENT '商品描述',
    price        DECIMAL(10,2) NOT NULL COMMENT '单价(元)',
    stock        INT           NOT NULL DEFAULT 0 COMMENT '库存',
    unit         VARCHAR(20)   NOT NULL DEFAULT '斤' COMMENT '单位',
    main_image   VARCHAR(500)           DEFAULT NULL COMMENT '商品主图URL',
    lifecycle    TINYINT       NOT NULL DEFAULT 0 COMMENT '0=种植中, 1=预售中, 2=成熟待采摘, 3=销售中, 4=售罄',
    audit_status TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待审核, 1=通过, 2=驳回',
    audit_reason VARCHAR(500)           DEFAULT NULL COMMENT '审核意见',
    sales_count  INT           NOT NULL DEFAULT 0 COMMENT '销量',
    view_count   INT           NOT NULL DEFAULT 0 COMMENT '浏览量',
    version      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id),
    KEY idx_farmer_id (farmer_id),
    KEY idx_audit_status (audit_status),
    KEY idx_lifecycle (lifecycle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='果农商品表(果农×SPU 可售商品, V1 最小销售单元)';

CREATE TABLE IF NOT EXISTS t_product_attribute (
    id          BIGINT        NOT NULL COMMENT '商品属性值ID(雪花)',
    product_id  BIGINT        NOT NULL COMMENT '关联商品ID',
    attr_name   VARCHAR(50)   NOT NULL COMMENT '属性名',
    attr_value  VARCHAR(255)  NOT NULL COMMENT '属性取值(如"5斤装")',
    extra_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '该取值的加价金额',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id),
    UNIQUE KEY uk_product_attr (product_id, attr_name, attr_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性值表';

CREATE TABLE IF NOT EXISTS t_product_image (
    id          BIGINT       NOT NULL COMMENT '商品图片ID(雪花)',
    product_id  BIGINT       NOT NULL COMMENT '关联商品ID',
    image_url   VARCHAR(500) NOT NULL COMMENT '图片URL',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品图片表';
