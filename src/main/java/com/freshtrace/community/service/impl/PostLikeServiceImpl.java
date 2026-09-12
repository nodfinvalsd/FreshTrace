package com.freshtrace.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.community.entity.Post;
import com.freshtrace.community.entity.PostLike;
import com.freshtrace.community.mapper.PostLikeMapper;
import com.freshtrace.community.mapper.PostMapper;
import com.freshtrace.community.service.PostLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞实现（Phase 6 Day 3）。
 * <p>
 * 一致性设计：t_post_like 为事实表（UNIQUE(post_id,user_id) 防重复），
 * t_post.like_count 为冗余计数器，两者在同一事务内更新。
 * 点赞/取消点赞均幂等：重复点赞、取消未点赞不报错，计数器不重复变更。
 * 取消点赞走物理删除（PostLikeMapper.physicalDelete），避免逻辑删除残留占用唯一键。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostLikeServiceImpl implements PostLikeService {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        requirePost(postId);
        if (alreadyLiked(userId, postId)) {
            return;
        }
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        try {
            postLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 并发重复点赞：UNIQUE(post_id, user_id) 兜底，幂等返回不再重复计数
            return;
        }
        postMapper.incrementLikeCount(postId);
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        requirePost(postId);
        int deleted = postLikeMapper.physicalDelete(postId, userId);
        if (deleted > 0) {
            postMapper.decrementLikeCount(postId);
        }
    }

    private boolean alreadyLiked(Long userId, Long postId) {
        return postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId)) > 0;
    }

    private void requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
