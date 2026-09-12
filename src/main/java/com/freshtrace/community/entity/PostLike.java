package com.freshtrace.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子点赞。UNIQUE(post_id, user_id) 防重复点赞。
 * <p>
 * 注意：取消点赞走物理删除（PostLikeMapper.physicalDelete），不使用逻辑删除——
 * 否则 deleted=0/1 的历史行会持续占用唯一键，导致取消后无法再次点赞。
 * BaseEntity.deleted 字段保留以对齐持久化规范，业务上恒为 0。
 */
@TableName("t_post_like")
@Data
@EqualsAndHashCode(callSuper = true)
public class PostLike extends BaseEntity {

    private Long postId;

    private Long userId;
}
