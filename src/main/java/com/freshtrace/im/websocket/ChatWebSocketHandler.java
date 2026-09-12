package com.freshtrace.im.websocket;

import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.support.ChatMessagePusher;
import com.freshtrace.im.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天 WebSocket 处理器：连接生命周期 + 帧路由。
 * <p>
 * 身份来自握手阶段写入的 session attributes（{@link JwtHandshakeInterceptor}），
 * 处理器只做协议分流，业务规则（归属校验、落库、推送）全部下沉到 {@link ChatMessageService}。
 * <p>
 * 失败不粗暴断连：可预期错误以 ERROR 帧回给发送方，保证前端能提示并继续发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String TYPE_PING = "PING";

    private static final String TYPE_PONG = "PONG";

    private static final String TYPE_CHAT = "CHAT";

    private final SessionManager sessionManager;

    private final ObjectMapper objectMapper;

    private final ChatMessageService chatMessageService;

    private final ChatMessagePusher chatMessagePusher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = currentUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionManager.register(userId, session);
        log.info("chat connected, userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = currentUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        String payload = message.getPayload();
        String type = readType(payload);
        if (TYPE_PING.equals(type)) {
            sessionManager.send(userId, toJson(Map.of("type", TYPE_PONG)));
            return;
        }
        if (TYPE_CHAT.equals(type)) {
            handleChat(userId, payload);
            return;
        }
        log.debug("chat frame type not supported, userId={}, type={}", userId, type);
    }

    /**
     * 处理聊天帧：解析 → 落库 → 回执；任何可预期异常都转为 ERROR 帧而非断连。
     */
    private void handleChat(Long userId, String payload) {
        String clientMsgId = null;
        try {
            ChatMessageDTO dto = objectMapper.readValue(payload, ChatMessageDTO.class);
            clientMsgId = dto.getClientMsgId();
            ChatMessageVO message = chatMessageService.send(userId, dto);
            // 回执发送方，前端据此把「发送中」置为「已发送」
            chatMessagePusher.pushAck(userId, clientMsgId, message);
        } catch (BizException e) {
            chatMessagePusher.pushError(userId, e.getCode(), e.getMessage(), clientMsgId);
        } catch (Exception e) {
            log.error("handle chat frame failed, userId={}", userId, e);
            chatMessagePusher.pushError(userId, ErrorCode.SYSTEM_ERROR.getCode(),
                    ErrorCode.SYSTEM_ERROR.getMsg(), clientMsgId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = currentUserId(session);
        if (userId != null) {
            sessionManager.remove(userId, session);
            log.info("chat disconnected, userId={}, sessionId={}, status={}", userId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("chat transport error, sessionId={}", session.getId(), exception);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 从 session attributes 取握手阶段写入的登录用户ID；为空说明非法连接。
     */
    private Long currentUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }

    /**
     * 只解析出帧类型，用于路由；解析失败返回 null 由调用方忽略。
     */
    private String readType(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode type = node.get("type");
            return type == null ? null : type.asString();
        } catch (Exception e) {
            log.debug("invalid chat frame payload: {}", payload);
            return null;
        }
    }

    private String toJson(Map<String, ?> payload) {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }

    /**
     * 静默关闭连接，用于非法连接与传输异常场景。
     */
    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception e) {
            log.debug("close chat session failed, sessionId={}", session.getId(), e);
        }
    }
}
