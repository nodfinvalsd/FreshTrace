package com.freshtrace.im.support;

import com.freshtrace.im.vo.ChatMessageVO;
import com.freshtrace.im.websocket.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天帧推送器：统一构造 WebSocket 协议帧并交给 {@link SessionManager} 发送。
 * <p>
 * 抽出来的意义：业务服务只关心「推给谁、推什么」，帧格式与在线判断集中在此，
 * 后续多实例改造（Redis Pub/Sub 广播）时只需替换本类实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePusher {

    /** 新消息帧 */
    public static final String TYPE_CHAT = "CHAT";

    /** 发送回执帧（服务端确认已落库） */
    public static final String TYPE_ACK = "ACK";

    /** 错误帧（内容非法、越权、系统异常等） */
    public static final String TYPE_ERROR = "ERROR";

    private final SessionManager sessionManager;

    private final ObjectMapper objectMapper;

    /**
     * 向接收方推送新消息；接收方离线时返回 false（消息已落库，待其上线后拉历史）。
     */
    public boolean pushNewMessage(Long receiverUserId, ChatMessageVO message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_CHAT);
        frame.put("conversationId", message.getConversationId());
        frame.put("messageId", message.getMessageId());
        frame.put("senderId", message.getSenderId());
        frame.put("senderRole", message.getSenderRole());
        frame.put("content", message.getContent());
        frame.put("createTime", message.getCreateTime());
        return send(receiverUserId, frame);
    }

    /**
     * 向发送方回执 ACK：带上 clientMsgId，前端据此把「发送中」气泡置为「已发送」。
     */
    public void pushAck(Long senderUserId, String clientMsgId, ChatMessageVO message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_ACK);
        frame.put("clientMsgId", clientMsgId);
        frame.put("conversationId", message.getConversationId());
        frame.put("messageId", message.getMessageId());
        frame.put("createTime", message.getCreateTime());
        send(senderUserId, frame);
    }

    /**
     * 推送错误帧。用于内容为空/超长、非会话成员等可预期失败，避免直接断开连接。
     */
    public void pushError(Long userId, Integer code, String msg, String clientMsgId) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_ERROR);
        frame.put("clientMsgId", clientMsgId);
        frame.put("code", code);
        frame.put("msg", msg);
        send(userId, frame);
    }

    private boolean send(Long userId, Map<String, Object> frame) {
        if (userId == null) {
            return false;
        }
        return sessionManager.send(userId, objectMapper.writeValueAsString(frame));
    }
}
