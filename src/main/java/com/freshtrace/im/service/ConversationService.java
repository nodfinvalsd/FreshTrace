package com.freshtrace.im.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.dto.ConversationQueryDTO;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.vo.ConversationVO;

/**
 * 会话服务（Phase 8 Day 2）。
 */
public interface ConversationService {

    /**
     * 买家发起会话（幂等）：同一买家与果农仅一条会话，UNIQUE(user_id, farmer_id) 兜底并发。
     */
    ConversationVO create(Long userId, ConversationCreateDTO dto);

    /**
     * 会话列表：果农视角按 farmer_id，买家视角按 user_id；按最后消息时间倒序。
     */
    PageVO<ConversationVO> list(Long userId, ConversationQueryDTO query);

    /**
     * 校验登录用户是否为会话参与方并返回会话，供消息发送/已读复用。
     */
    Conversation requireMember(Long conversationId, Long userId);

    /**
     * 标记会话来消息已读：所在视角方的会话未读清零，并把对方发来的消息批量置为已读。
     */
    void markRead(Long userId, Long conversationId);

    /**
     * 当前登录用户的总未读数（果农看 farmer_id 汇总，买家看 user_id 汇总）。
     */
    long unreadTotal(Long userId);
}
