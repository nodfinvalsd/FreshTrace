package com.freshtrace.community.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 动态详情 VO。在列表 VO 基础上附加嵌套评论树。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDetailVO extends PostVO {

    private List<CommentVO> comments;
}
