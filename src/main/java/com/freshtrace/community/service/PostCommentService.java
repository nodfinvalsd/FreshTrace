package com.freshtrace.community.service;

import com.freshtrace.community.dto.CommentCreateDTO;
import com.freshtrace.community.vo.CommentVO;

import java.util.List;

public interface PostCommentService {

    CommentVO createComment(Long userId, Long postId, CommentCreateDTO dto);

    /**
     * 查询某动态的全部评论并组装为评论树（顶层评论 + children 嵌套回复）。
     */
    List<CommentVO> listTree(Long postId);
}
