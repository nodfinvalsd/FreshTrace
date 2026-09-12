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

-- ============ Phase 3 交易中心 ============

DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_refund;
DROP TABLE IF EXISTS t_review;
DROP TABLE IF EXISTS t_sub_order;
DROP TABLE IF EXISTS t_payment;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_shopping_cart;

CREATE TABLE t_shopping_cart (
    id            BIGINT       NOT NULL COMMENT '购物车项ID(雪花)',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    product_id    BIGINT       NOT NULL COMMENT '商品ID',
    spec_snapshot VARCHAR(500) NOT NULL DEFAULT '' COMMENT '规格快照(JSON)，无规格为空串',
    quantity      INT          NOT NULL DEFAULT 1 COMMENT '数量',
    selected      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否勾选 0=否 1=是',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product_spec (user_id, product_id, spec_snapshot),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

CREATE TABLE t_order (
    id               BIGINT        NOT NULL COMMENT '主订单ID(雪花)',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号(雪花)',
    user_id          BIGINT        NOT NULL COMMENT '买家ID',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    pay_amount       DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待支付, 1=已支付, 2=部分发货, 3=全部发货, 4=已完成, 5=已取消',
    address_snapshot VARCHAR(1000) NOT NULL COMMENT '收货地址快照(JSON, 下单时保存)',
    remark           VARCHAR(500)           DEFAULT NULL COMMENT '买家备注',
    paid_at          DATETIME               DEFAULT NULL COMMENT '支付时间',
    finished_at      DATETIME               DEFAULT NULL COMMENT '完成时间',
    cancelled_at     DATETIME               DEFAULT NULL COMMENT '取消时间',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id_status (user_id, status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主订单表';

CREATE TABLE t_sub_order (
    id                BIGINT        NOT NULL COMMENT '子订单ID(雪花)',
    sub_order_no      VARCHAR(32)   NOT NULL COMMENT '子订单号',
    order_id          BIGINT        NOT NULL COMMENT '主订单ID',
    farmer_id         BIGINT        NOT NULL COMMENT '果农ID',
    amount            DECIMAL(10,2) NOT NULL COMMENT '子订单金额',
    status            TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待支付, 1=待发货, 2=待收货, 3=已完成, 4=退款中, 5=已退款, 6=已取消',
    logistics_company VARCHAR(100)           DEFAULT NULL COMMENT '物流公司',
    logistics_no      VARCHAR(100)           DEFAULT NULL COMMENT '物流单号',
    shipped_at        DATETIME               DEFAULT NULL COMMENT '发货时间',
    received_at       DATETIME               DEFAULT NULL COMMENT '收货时间',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_order_no (sub_order_no),
    KEY idx_order_id (order_id),
    KEY idx_farmer_id_status (farmer_id, status),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子订单表(按果农拆单)';

CREATE TABLE t_order_item (
    id            BIGINT        NOT NULL COMMENT '订单明细ID(雪花)',
    sub_order_id  BIGINT        NOT NULL COMMENT '关联子订单ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    product_title VARCHAR(200)  NOT NULL COMMENT '商品标题快照',
    product_image VARCHAR(500)           DEFAULT NULL COMMENT '商品图片快照',
    spec_snapshot VARCHAR(500)           DEFAULT NULL COMMENT '规格快照(JSON)',
    price         DECIMAL(10,2) NOT NULL COMMENT '成交单价(含规格加价)',
    quantity      INT           NOT NULL COMMENT '数量',
    total_price   DECIMAL(10,2) NOT NULL COMMENT '小计(price*quantity)',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sub_order_id (sub_order_id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品明细表';

CREATE TABLE t_payment (
    id          BIGINT        NOT NULL COMMENT '支付记录ID(雪花)',
    payment_no  VARCHAR(32)   NOT NULL COMMENT '支付流水号',
    order_id    BIGINT        NOT NULL COMMENT '关联主订单ID',
    amount      DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    pay_method  VARCHAR(20)   NOT NULL DEFAULT 'SIMULATED' COMMENT '支付方式',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待支付, 1=成功, 2=失败',
    paid_at     DATETIME               DEFAULT NULL COMMENT '支付时间',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付表';

CREATE TABLE t_refund (
    id              BIGINT        NOT NULL COMMENT '退款记录ID(雪花)',
    refund_no       VARCHAR(32)   NOT NULL COMMENT '退款流水号',
    sub_order_id    BIGINT        NOT NULL COMMENT '关联子订单ID',
    farmer_id       BIGINT        NOT NULL COMMENT '被退款果农ID(创建时从子订单快照)',
    user_id         BIGINT        NOT NULL COMMENT '申请人ID',
    amount          DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    reason          VARCHAR(500)  NOT NULL COMMENT '退款原因',
    evidence_images VARCHAR(2000)          DEFAULT NULL COMMENT '凭证图片(JSON)',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待处理, 1=果农同意, 2=果农拒绝, 3=客服仲裁, 4=已退款, 5=已驳回',
    handler_id      BIGINT                 DEFAULT NULL COMMENT '处理人ID',
    handle_reason   VARCHAR(500)           DEFAULT NULL COMMENT '处理意见',
    handled_at      DATETIME               DEFAULT NULL COMMENT '处理时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_sub_order_id (sub_order_id),
    KEY idx_user_id (user_id),
    KEY idx_farmer_status (farmer_id, status),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款表';

-- ============ Phase 4 履约 & 评价 ============

CREATE TABLE t_review (
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

-- ============ Phase 5 溯源系统 ============

DROP TABLE IF EXISTS t_trace_node;

CREATE TABLE t_trace_node (
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

-- ============ Phase 6 动态社区 ============

DROP TABLE IF EXISTS t_post_image;
DROP TABLE IF EXISTS t_post_comment;
DROP TABLE IF EXISTS t_post_like;
DROP TABLE IF EXISTS t_post;

CREATE TABLE t_post (
    id            BIGINT       NOT NULL COMMENT '动态ID(雪花)',
    farmer_id     BIGINT       NOT NULL COMMENT '发布果农ID',
    product_id    BIGINT                DEFAULT NULL COMMENT '关联商品ID',
    trace_node_id BIGINT                DEFAULT NULL COMMENT '关联溯源节点ID',
    content       TEXT         NOT NULL COMMENT '正文',
    like_count    INT          NOT NULL DEFAULT 0 COMMENT '点赞数(冗余)',
    comment_count INT          NOT NULL DEFAULT 0 COMMENT '评论数(冗余)',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_farmer_id (farmer_id),
    KEY idx_product_id (product_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态帖子表';

CREATE TABLE t_post_image (
    id         BIGINT       NOT NULL COMMENT '主键(雪花)',
    post_id    BIGINT       NOT NULL COMMENT '帖子ID',
    image_url  VARCHAR(500) NOT NULL COMMENT '图片URL',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子图片表';

CREATE TABLE t_post_comment (
    id          BIGINT       NOT NULL COMMENT '评论ID(雪花)',
    post_id     BIGINT       NOT NULL COMMENT '帖子ID',
    user_id     BIGINT       NOT NULL COMMENT '评论人ID',
    parent_id   BIGINT                DEFAULT NULL COMMENT '父评论ID',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_post_id (post_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';

CREATE TABLE t_post_like (
    id          BIGINT   NOT NULL COMMENT '点赞ID(雪花)',
    post_id     BIGINT   NOT NULL COMMENT '帖子ID',
    user_id     BIGINT   NOT NULL COMMENT '点赞人ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

-- ============ Phase 7 预售系统 ============

DROP TABLE IF EXISTS t_presale_reservation;
DROP TABLE IF EXISTS t_presale;

CREATE TABLE t_presale (
    id                BIGINT   NOT NULL COMMENT '预售配置ID(雪花)',
    product_id        BIGINT   NOT NULL COMMENT '关联商品ID(1:1)',
    farmer_id         BIGINT   NOT NULL COMMENT '所属果农ID',
    presale_start     DATETIME NOT NULL COMMENT '预售开始时间',
    presale_end       DATETIME NOT NULL COMMENT '预售截止(预计成熟)时间',
    expected_harvest  DATETIME          DEFAULT NULL COMMENT '预计成熟采摘时间',
    max_reservations  INT      NOT NULL DEFAULT 0 COMMENT '最大预约数,0=不限',
    reservation_count INT      NOT NULL DEFAULT 0 COMMENT '当前预约数(冗余)',
    status            TINYINT  NOT NULL DEFAULT 1 COMMENT '0=关闭,1=进行中,2=已结束',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id),
    KEY idx_farmer_id (farmer_id),
    KEY idx_status_end (status, presale_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预售配置表';

CREATE TABLE t_presale_reservation (
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

-- ============ Phase 8 即时通讯 ============

DROP TABLE IF EXISTS t_chat_message;
DROP TABLE IF EXISTS t_conversation;

CREATE TABLE t_conversation (
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

CREATE TABLE t_chat_message (
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
