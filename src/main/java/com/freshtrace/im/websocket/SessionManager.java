package com.freshtrace.im.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器：维护 userId → 在线 Session 映射。
 * <p>
 * - 单用户单连接：同一用户重复连接时替换旧连接并关闭，避免会话泄漏；<br>
 * - 线程安全：Session 用 {@link ConcurrentWebSocketSessionDecorator} 包装，保证并发 {@code sendMessage} 安全
 *   （原生 WebSocketSession 并非线程安全，业务线程与心跳线程可能同时发送）；<br>
 * - V1 为单实例内存态，多实例部署时可将 push 替换为 Redis Pub/Sub 广播。
 */
@Slf4j
@Component
public class SessionManager {

    /** 单次发送超时（毫秒）：超过则判定连接异常并清理 */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    /** 发送缓冲上限（字节）：客户端消费过慢导致积压超限时关闭连接，防止内存膨胀 */
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册用户连接；若已存在旧连接则关闭旧连接（同一用户只保留最新一条）。
     */
    public void register(Long userId, WebSocketSession session) {
        WebSocketSession wrapped = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        WebSocketSession previous = sessions.put(userId, wrapped);
        if (previous != null && !previous.getId().equals(wrapped.getId())) {
            closeQuietly(previous);
            log.info("replace previous chat session, userId={}, oldSession={}", userId, previous.getId());
        }
    }

    /**
     * 移除连接：仅当映射中的连接仍是当前连接时移除（用 sessionId 比对），
     * 避免「旧连接关闭事件晚到」把用户刚建立的新连接误删。
     */
    public void remove(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, current) ->
                current.getId().equals(session.getId()) ? null : current);
    }

    /**
     * 用户是否在线（存在且连接未关闭）。
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 向指定用户推送文本帧。
     *
     * @return true=已发送；false=离线或发送失败（调用方据此决定是否只保留落库结果）
     */
    public boolean send(Long userId, String payload) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException e) {
            // 发送失败说明连接已不可用，主动清理，避免后续继续尝试
            log.warn("push chat message failed, userId={}", userId, e);
            remove(userId, session);
            closeQuietly(session);
            return false;
        }
    }

    /**
     * 静默关闭连接，忽略关闭过程中的异常。
     */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.debug("close chat session failed, session={}", session.getId(), e);
        }
    }
}
