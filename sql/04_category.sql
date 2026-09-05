-- FreshTrace 商品中心 - 品类 & SPU 模块 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，create_time/update_time/deleted 为通用字段

CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT       NOT NULL COMMENT '品类ID(雪花)',
    name        VARCHAR(50)  NOT NULL COMMENT '品类名(如"芒果")',
    icon_url    VARCHAR(500)          DEFAULT NULL COMMENT '图标URL',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值(越小越靠前)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='水果品类表';

CREATE TABLE IF NOT EXISTS t_spu (
    id          BIGINT       NOT NULL COMMENT 'SPU ID(雪花)',
    category_id BIGINT       NOT NULL COMMENT '所属品类ID',
    name        VARCHAR(100) NOT NULL COMMENT 'SPU名称(如"海南红心芒果")',
    variety     VARCHAR(100)          DEFAULT NULL COMMENT '品种',
    origin      VARCHAR(100)          DEFAULT NULL COMMENT '产地',
    description TEXT                 DEFAULT NULL COMMENT '描述',
    main_image  VARCHAR(500)          DEFAULT NULL COMMENT '主图URL',
    tags        VARCHAR(500)          DEFAULT NULL COMMENT '标签(JSON数组)',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=下架, 1=上架',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU表';

CREATE TABLE IF NOT EXISTS t_spu_attribute (
    id          BIGINT       NOT NULL COMMENT '属性模板ID(雪花)',
    spu_id      BIGINT       NOT NULL COMMENT '所属SPU ID',
    attr_name   VARCHAR(50)  NOT NULL COMMENT '属性名(如"规格")',
    attr_values VARCHAR(500) NOT NULL COMMENT '可选值(JSON数组, 如["5斤装","10斤装"])',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU属性模板表';
