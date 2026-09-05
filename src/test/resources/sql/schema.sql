DROP TABLE IF EXISTS t_product_image;
DROP TABLE IF EXISTS t_product_attribute;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_spu_attribute;
DROP TABLE IF EXISTS t_spu;
DROP TABLE IF EXISTS t_category;
DROP TABLE IF EXISTS t_farmer;
DROP TABLE IF EXISTS t_address;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id            BIGINT       NOT NULL COMMENT '用户ID(雪花)',
    username      VARCHAR(50)  NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    nickname      VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    avatar_url    VARCHAR(500)          DEFAULT NULL COMMENT '头像URL',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
    role          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=普通用户, 1=平台管理员',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用, 1=正常',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE t_address (
    id             BIGINT       NOT NULL COMMENT '地址ID(雪花)',
    user_id        BIGINT       NOT NULL COMMENT '用户ID',
    receiver_name  VARCHAR(50)  NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(20)  NOT NULL COMMENT '收货电话',
    province       VARCHAR(30)  NOT NULL COMMENT '省',
    city           VARCHAR(30)  NOT NULL COMMENT '市',
    district       VARCHAR(30)  NOT NULL COMMENT '区',
    detail         VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址 0=否 1=是',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

CREATE TABLE t_farmer (
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

CREATE TABLE t_category (
    id          BIGINT       NOT NULL COMMENT '品类ID(雪花)',
    name        VARCHAR(50)  NOT NULL COMMENT '品类名',
    icon_url    VARCHAR(500)          DEFAULT NULL COMMENT '图标URL',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='水果品类表';

CREATE TABLE t_spu (
    id          BIGINT       NOT NULL COMMENT 'SPU ID(雪花)',
    category_id BIGINT       NOT NULL COMMENT '所属品类ID',
    name        VARCHAR(100) NOT NULL COMMENT 'SPU名称',
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

CREATE TABLE t_spu_attribute (
    id          BIGINT       NOT NULL COMMENT '属性模板ID(雪花)',
    spu_id      BIGINT       NOT NULL COMMENT '所属SPU ID',
    attr_name   VARCHAR(50)  NOT NULL COMMENT '属性名',
    attr_values VARCHAR(500) NOT NULL COMMENT '可选值(JSON数组)',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU属性模板表';

CREATE TABLE t_product (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='果农商品表';

CREATE TABLE t_product_attribute (
    id          BIGINT        NOT NULL COMMENT '商品属性值ID(雪花)',
    product_id  BIGINT        NOT NULL COMMENT '关联商品ID',
    attr_name   VARCHAR(50)   NOT NULL COMMENT '属性名',
    attr_value  VARCHAR(255)  NOT NULL COMMENT '属性取值',
    extra_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '加价金额',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id),
    UNIQUE KEY uk_product_attr (product_id, attr_name, attr_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性值表';

CREATE TABLE t_product_image (
    id          BIGINT       NOT NULL COMMENT '商品图片ID(雪花)',
    product_id  BIGINT       NOT NULL COMMENT '关联商品ID',
    image_url   VARCHAR(500) NOT NULL COMMENT '图片URL',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品图片表';
