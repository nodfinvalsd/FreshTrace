package com.freshtrace.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.im.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会话 Mapper。
 * <p>
 * 除基础 CRUD 外，提供「消息发送后同步摘要/未读」「标记已读清零」「未读总数」等原子更新，
 * 避免在业务层做「读-改-写」造成并发丢失。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 消息落库后同步会话摘要与未读计数，必须与消息 INSERT 处于同一事务。
     * <p>
     * 用 buyerDelta / farmerDelta 指定「接收方」这一侧的未读 +1（另一侧 +0），
     * 借助数据库原子自增避免「读-改-写」竞态；last_message_at 用数据库时间保证有序。
     *
     * @param id          会话ID
     * @param content     用于列表展示的最后一条消息摘要
     * @param buyerDelta  买家未读增量（接收方为买家时 1，否则 0）
     * @param farmerDelta 果农未读增量（接收方为果农时 1，否则 0）
     * @return 受影响行数，1 表示成功
     */
    @Update("""
            UPDATE t_conversation
            SET last_message = #{content},
                last_message_at = NOW(),
                unread_user = unread_user + #{buyerDelta},
                unread_farmer = unread_farmer + #{farmerDelta}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateAfterMessage(@Param("id") Long id,
                           @Param("content") String content,
                           @Param("buyerDelta") int buyerDelta,
                           @Param("farmerDelta") int farmerDelta);

    /**
     * 买家进入会话后把买家未读清零（标记已读时调用，随消息批量更新在同一事务内）。
     */
    @Update("UPDATE t_conversation SET unread_user = 0 WHERE id = #{id} AND deleted = 0")
    int resetUnreadUser(@Param("id") Long id);

    /**
     * 果农进入会话后把果农未读清零。
     */
    @Update("UPDATE t_conversation SET unread_farmer = 0 WHERE id = #{id} AND deleted = 0")
    int resetUnreadFarmer(@Param("id") Long id);

    /**
     * 买家未读总数（会话列表页角标）；无数据返回 0。
     */
    @Select("SELECT COALESCE(SUM(unread_user), 0) FROM t_conversation WHERE user_id = #{userId} AND deleted = 0")
    long sumUnreadUser(@Param("userId") Long userId);

    /**
     * 果农未读总数；无数据返回 0。
     */
    @Select("SELECT COALESCE(SUM(unread_farmer), 0) FROM t_conversation WHERE farmer_id = #{farmerId} AND deleted = 0")
    long sumUnreadFarmer(@Param("farmerId") Long farmerId);
}
