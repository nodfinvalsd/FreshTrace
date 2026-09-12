package com.freshtrace.im.service;

import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.vo.ChatHistoryVO;
import com.freshtrace.im.vo.ChatMessageVO;

/**
 * 聊天消息服务（Phase 8 Day 3）。
 */
public interface ChatMessageService {

    /**
     * 发送消息：校验会话归属 → 事务内落库并更新会话摘要/未读数 → 提交后推送接收方。
     *
     * @param senderId 发送者登录账号 ID（来自登录态，非客户端传入）
     * @return 落库后的消息视图
     */
    ChatMessageVO send(Long senderId, ChatMessageDTO dto);

    /**
     * 历史消息（游标分页，按消息 ID 倒序）。
     *
     * @param lastId 上一页最旧消息 ID，首页传 null
     */
    ChatHistoryVO history(Long userId, Long conversationId, Long lastId, int size);
}
