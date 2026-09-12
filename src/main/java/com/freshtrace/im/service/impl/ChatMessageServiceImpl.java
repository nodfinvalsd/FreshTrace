package com.freshtrace.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.entity.ChatMessage;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.support.ChatMessagePusher;
import com.freshtrace.im.vo.ChatHistoryVO;
import com.freshtrace.im.vo.ChatMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 聊天消息服务实现（Phase 8 Day 3）。
 * <p>
 * 核心不变量：
 * <ol>
 *     <li>身份不信任客户端——senderRole 与接收方都由服务端按会话成员关系推导；</li>
 *     <li>消息与「会话摘要 + 未读计数」在同一事务内落库，保证列表与未读不漂移；</li>
 *     <li>接收方在线才实时推送，离线仅落库，上线后由历史消息接口补齐。</li>
 * </ol>
 */
@Service
@Slf4j
public class ChatMessageServiceImpl implements ChatMessageService {

    /** 与 t_chat_message.content VARCHAR(1000) 对齐 */
    private static final int MAX_CONTENT_LENGTH = 1000;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int ROLE_BUYER = 0;

    private static final int ROLE_FARMER = 1;

    private final ChatMessageMapper chatMessageMapper;

    private final ConversationMapper conversationMapper;

    private final FarmerMapper farmerMapper;

    private final ConversationService conversationService;

    private final ChatMessagePusher chatMessagePusher;

    private final TransactionTemplate transactionTemplate;

    public ChatMessageServiceImpl(ChatMessageMapper chatMessageMapper,
                                  ConversationMapper conversationMapper,
                                  FarmerMapper farmerMapper,
                                  ConversationService conversationService,
                                  ChatMessagePusher chatMessagePusher,
                                  PlatformTransactionManager transactionManager) {
        this.chatMessageMapper = chatMessageMapper;
        this.conversationMapper = conversationMapper;
        this.farmerMapper = farmerMapper;
        this.conversationService = conversationService;
        this.chatMessagePusher = chatMessagePusher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public ChatMessageVO send(Long senderId, ChatMessageDTO dto) {
        if (dto == null || dto.getConversationId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话ID不能为空");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.CHAT_CONTENT_TOO_LONG);
        }

        // 1. 归属校验：只有会话参与方（买家本人 / 会话对应果农的账号）才能发言
        Conversation conversation = conversationService.requireMember(dto.getConversationId(), senderId);

        // 2. 服务端推导发送者身份与接收方，绝不信任客户端传入的角色
        boolean senderIsFarmer = !conversation.getUserId().equals(senderId);
        int senderRole = senderIsFarmer ? ROLE_FARMER : ROLE_BUYER;
        Long receiverUserId = senderIsFarmer
                ? conversation.getUserId()
                : requireFarmerUserId(conversation.getFarmerId());

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        message.setContent(content);
        message.setIsRead(0);

        // 3. 事务边界：消息 INSERT 与「会话摘要 + 接收方未读 +1」同生共死
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageMapper.insert(message);
            conversationMapper.updateAfterMessage(
                    conversation.getId(), content, senderIsFarmer ? 1 : 0, senderIsFarmer ? 0 : 1);
        });

        // 4. 事务提交后再推送：在线则实时到达，离线则仅落库待拉取
        ChatMessageVO vo = toVO(message);
        boolean online = chatMessagePusher.pushNewMessage(receiverUserId, vo);
        log.info("chat message saved, conversationId={}, messageId={}, senderId={}, receiverOnline={}",
                conversation.getId(), message.getId(), senderId, online);
        return vo;
    }

    @Override
    public ChatHistoryVO history(Long userId, Long conversationId, Long lastId, int size) {
        // 越权防护：非会话参与方不得读取历史
        conversationService.requireMember(conversationId, userId);

        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .lt(lastId != null, ChatMessage::getId, lastId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + (limit + 1));
        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

        // 多查一条用于判断是否还有更早的消息，再裁剪掉多余那条
        boolean hasMore = messages.size() > limit;
        if (hasMore) {
            messages = messages.subList(0, limit);
        }

        ChatHistoryVO vo = new ChatHistoryVO();
        vo.setRecords(messages.stream().map(this::toVO).toList());
        vo.setHasMore(hasMore);
        vo.setNextCursor(messages.isEmpty() ? null : messages.get(messages.size() - 1).getId());
        return vo;
    }

    /**
     * 买家发消息时，接收方是果农的登录账号 user_id（t_farmer.user_id）。
     */
    private Long requireFarmerUserId(Long farmerId) {
        Farmer farmer = farmerMapper.selectById(farmerId);
        if (farmer == null) {
            throw new BizException(ErrorCode.IM_TARGET_FARMER_INVALID);
        }
        return farmer.getUserId();
    }

    private ChatMessageVO toVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setMessageId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setSenderRole(message.getSenderRole());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
