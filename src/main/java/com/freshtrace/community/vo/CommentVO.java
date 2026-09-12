package com.freshtrace.community.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论展示 VO。children 由应用层按 parent_id 组装，形成评论树。
 */
@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private Long parentId;

    private String content;

    private LocalDateTime createdAt;

    /** 子评论（回复） */
    private List<CommentVO> children = new ArrayList<>();
}
