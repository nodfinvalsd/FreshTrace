package com.freshtrace.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.community.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 点赞数 +1（原子更新，与 t_post_like 插入同事务）。
     */
    @Update("UPDATE t_post SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementLikeCount(@Param("id") Long id);

    /**
     * 点赞数 -1（原子更新，且兜底不出现负数）。
     */
    @Update("UPDATE t_post SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0 AND deleted = 0")
    int decrementLikeCount(@Param("id") Long id);

    /**
     * 评论数 +1（原子更新，与 t_post_comment 插入同事务）。
     */
    @Update("UPDATE t_post SET comment_count = comment_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementCommentCount(@Param("id") Long id);
}
