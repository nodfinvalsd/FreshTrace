-- FreshTrace 动态社区 DDL
-- 约定：id 雪花ID(应用层生成，不使用 AUTO_INCREMENT)，遵循 BaseEntity 通用字段 create_time/update_time/deleted 逻辑删除
-- 帖子图片独立成表 t_post_image（与 t_product 主图 + t_product_image 图片库模式一致），不在 t_post 冗余 images JSON
-- 点赞表 t_post_like：UNIQUE(post_id, user_id) 防重复；取消赞走物理删除（逻辑删除会使唯一键残留导致无法再次点赞）
-- 计数冗余：like_count / comment_count 冗余在 t_post，与事实表同事务更新，避免读多写少场景 COUNT(*)

CREATE TABLE IF NOT EXISTS t_post (
    id            BIGINT       NOT NULL COMMENT '动态ID(雪花)',
    farmer_id     BIGINT       NOT NULL COMMENT '发布果农ID',
    product_id    BIGINT                DEFAULT NULL COMMENT '关联商品ID(可空)',
    trace_node_id BIGINT                DEFAULT NULL COMMENT '关联溯源节点ID(可空)',
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

CREATE TABLE IF NOT EXISTS t_post_image (
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

CREATE TABLE IF NOT EXISTS t_post_comment (
    id          BIGINT       NOT NULL COMMENT '评论ID(雪花)',
    post_id     BIGINT       NOT NULL COMMENT '帖子ID',
    user_id     BIGINT       NOT NULL COMMENT '评论人ID',
    parent_id   BIGINT                DEFAULT NULL COMMENT '父评论ID(自关联，支持嵌套回复)',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_post_id (post_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';

CREATE TABLE IF NOT EXISTS t_post_like (
    id          BIGINT   NOT NULL COMMENT '点赞ID(雪花)',
    post_id     BIGINT   NOT NULL COMMENT '帖子ID',
    user_id     BIGINT   NOT NULL COMMENT '点赞人ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';
