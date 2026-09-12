package com.freshtrace.community.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态展示 VO。附带果农果园名用于时间线展示，liked 表示当前登录用户是否已点赞。
 */
@Data
public class PostVO {

    private Long id;

    private Long farmerId;

    private String orchardName;

    private Long productId;

    private Long traceNodeId;

    private String content;

    private Integer likeCount;

    private Integer commentCount;

    private List<String> images;

    /** 当前登录用户是否已点赞；匿名访问恒为 false */
    private Boolean liked;

    private LocalDateTime createdAt;
}
