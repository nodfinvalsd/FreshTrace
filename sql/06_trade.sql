-- FreshTrace 交易中心 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，主表使用 create_time/update_time/deleted 通用字段
-- 主表 t_order / t_sub_order 增加 version 乐观锁
-- 从表 t_order_item 为不可变订单明细（下单后不改动），采用物理删除、不引入逻辑删除
-- 业务字段/索引/唯一约束与数据库设计文档 v1.1 一致

CREATE TABLE IF NOT EXISTS t_shopping_cart (
    id            BIGINT       NOT NULL COMMENT '购物车项ID(雪花)',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    product_id    BIGINT       NOT NULL COMMENT '商品ID',
    spec_snapshot VARCHAR(500) NOT NULL DEFAULT '' COMMENT '规格快照(JSON)，如[{"name":"规格","value":"5斤装","extraPrice":30.00}]；无规格为空串',
    quantity      INT          NOT NULL DEFAULT 1 COMMENT '数量',
    selected      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否勾选(结算用) 0=否 1=是',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product_spec (user_id, product_id, spec_snapshot),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

CREATE TABLE IF NOT EXISTS t_order (
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

CREATE TABLE IF NOT EXISTS t_sub_order (
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

CREATE TABLE IF NOT EXISTS t_order_item (
    id             BIGINT        NOT NULL COMMENT '订单明细ID(雪花)',
    sub_order_id   BIGINT        NOT NULL COMMENT '关联子订单ID',
    product_id     BIGINT        NOT NULL COMMENT '商品ID',
    product_title  VARCHAR(200)  NOT NULL COMMENT '商品标题快照',
    product_image  VARCHAR(500)           DEFAULT NULL COMMENT '商品图片快照',
    spec_snapshot  VARCHAR(500)           DEFAULT NULL COMMENT '规格快照(JSON)，含 extraPrice，可还原成交构成',
    price          DECIMAL(10,2) NOT NULL COMMENT '成交单价(含规格加价)，下单时锁定',
    quantity       INT           NOT NULL COMMENT '数量',
    total_price    DECIMAL(10,2) NOT NULL COMMENT '小计(price*quantity)',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sub_order_id (sub_order_id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品明细表';

CREATE TABLE IF NOT EXISTS t_payment (
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

CREATE TABLE IF NOT EXISTS t_refund (
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
