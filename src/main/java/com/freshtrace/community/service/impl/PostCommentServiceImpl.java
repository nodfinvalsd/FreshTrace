package com.freshtrace.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.community.dto.CommentCreateDTO;
import com.freshtrace.community.entity.Post;
import com.freshtrace.community.entity.PostComment;
import com.freshtrace.community.mapper.PostCommentMapper;
import com.freshtrace.community.mapper.PostMapper;
import com.freshtrace.community.service.PostCommentService;
import com.freshtrace.community.vo.CommentVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论实现（Phase 6 Day 3）。
 * <p>
 * - 发表评论：校验父评论属于同一动态，事务内 INSERT t_post_comment + t_post.comment_count+1；
 * - 评论树：一次查出该动态全部评论（按时间升序），应用层按 parent_id 组装树，避免递归 N+1；
 *   孤儿节点（父评论已被删除）上浮为顶层，保证不丢数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostCommentServiceImpl implements PostCommentService {

    private final PostCommentMapper postCommentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public CommentVO createComment(Long userId, Long postId, CommentCreateDTO dto) {
        requirePost(postId);
        if (dto.getParentId() != null) {
            PostComment parent = postCommentMapper.selectById(dto.getParentId());
            if (parent == null || !parent.getPostId().equals(postId)) {
                throw new BizException(ErrorCode.COMMENT_NOT_FOUND, "父评论不存在或不属于该动态");
            }
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(dto.getParentId());
        comment.setContent(dto.getContent());
        postCommentMapper.insert(comment);

        postMapper.incrementCommentCount(postId);
        return toVO(comment, userMapper.selectById(userId));
    }

    @Override
    public List<CommentVO> listTree(Long postId) {
        List<PostComment> comments = postCommentMapper.selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPostId, postId)
                .orderByAsc(PostComment::getCreateTime)
                .orderByAsc(PostComment::getId));
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, User> userMap = loadUsers(comments);
        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        for (PostComment comment : comments) {
            voMap.put(comment.getId(), toVO(comment, userMap.get(comment.getUserId())));
        }

        List<CommentVO> roots = new ArrayList<>();
        for (PostComment comment : comments) {
            CommentVO vo = voMap.get(comment.getId());
            Long parentId = comment.getParentId();
            CommentVO parent = parentId == null ? null : voMap.get(parentId);
            if (parent == null) {
                // 顶层评论，或父评论已删除的孤儿节点 → 上浮为顶层
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    private Map<Long, User> loadUsers(List<PostComment> comments) {
        List<Long> userIds = comments.stream().map(PostComment::getUserId).distinct().toList();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
    }

    private void requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private CommentVO toVO(PostComment comment, User user) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatarUrl(user.getAvatarUrl());
        }
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreateTime());
        return vo;
    }
}
