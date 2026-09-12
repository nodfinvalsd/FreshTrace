package com.freshtrace.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子评论。parent_id 自关联支持嵌套回复，V1 查询时一次拉取全量评论，
 * 应用层按 parent_id 组装评论树，避免递归 N+1。
 */
@TableName("t_post_comment")
@Data
@EqualsAndHashCode(callSuper = true)
public class PostComment extends BaseEntity {

    private Long postId;

    private Long userId;

    private Long parentId;

    private String content;
}
