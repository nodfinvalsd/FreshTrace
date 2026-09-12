package com.freshtrace.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.community.entity.PostLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    /**
     * 取消点赞（物理删除）。
     * <p>
     * 不使用 {@code delete()}/{@code deleteById()} 的逻辑删除：deleted 历史行会持续占用
     * UNIQUE(post_id, user_id)，导致取消后无法再次点赞。此处显式物理删除绕开 {@code @TableLogic}。
     */
    @Delete("DELETE FROM t_post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int physicalDelete(@Param("postId") Long postId, @Param("userId") Long userId);
}
