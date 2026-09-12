package com.freshtrace.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子图片。一个帖子多张图，按 sort_order 升序展示。
 * <p>
 * 独立成表而非在 t_post 冗余 images JSON：帖子图片数量较多且需要排序，
 * 与 t_product 主图 + t_product_image 图片库模式保持设计一致性。
 */
@TableName("t_post_image")
@Data
@EqualsAndHashCode(callSuper = true)
public class PostImage extends BaseEntity {

    private Long postId;

    private String imageUrl;

    private Integer sortOrder;
}
