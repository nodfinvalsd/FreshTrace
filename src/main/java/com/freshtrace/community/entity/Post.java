package com.freshtrace.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动态帖子。果农发布的内容主体，支持图文 + 可选关联商品/溯源节点。
 * <p>
 * 计数冗余设计：like_count / comment_count 冗余在 t_post，读多写少场景避免 COUNT(*)；
 * 事实表为 t_post_like / t_post_comment，计数器与 fact 表在同一事务内更新保证一致。
 * <p>
 * 图片不在此冗余：统一由 t_post_image 管理（与 t_product 主图 + t_product_image 图片库模式一致）。
 * <p>
 * Phase 6 Day 1 仅建表与持久化层准备，发布/互动业务后续实现。
 */
@TableName("t_post")
@Data
@EqualsAndHashCode(callSuper = true)
public class Post extends BaseEntity {

    private Long farmerId;

    private Long productId;

    private Long traceNodeId;

    private String content;

    private Integer likeCount;

    private Integer commentCount;
}
