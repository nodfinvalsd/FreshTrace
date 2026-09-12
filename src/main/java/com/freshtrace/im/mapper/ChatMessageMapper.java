package com.freshtrace.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.im.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 聊天消息 Mapper。
 * <p>
 * 历史消息按 (conversation_id, id) 游标分页；已读用一条批量 UPDATE 完成，避免逐条操作。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 批量把会话内「对方发来的未读消息」标记为已读。
     * <p>
     * 一条 SQL 覆盖整个会话，避免逐条 update；条件 {@code sender_id <> readerId}
     * 保证只标记接收方视角的未读，不会把读方自己发的消息也算进去。
     *
     * @param conversationId 会话ID
     * @param readerId       读方登录账号 user_id
     * @return 受影响行数
     */
    @Update("""
            UPDATE t_chat_message
            SET is_read = 1
            WHERE conversation_id = #{conversationId}
              AND sender_id <> #{readerId}
              AND is_read = 0
              AND deleted = 0
            """)
    int markMessagesRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);
}
