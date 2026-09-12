package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.im.websocket.ChatWebSocketHandler;
import com.freshtrace.im.websocket.SessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 8 Day 1：WebSocket 基建验证。
 * <p>
 * - 组件注册；<br>
 * - 握手阶段非法 token 被拒；<br>
 * - 合法 token 建连后可收发（PING → PONG）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImWebSocketHandshakeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ApplicationContext context;

    @Test
    void webSocketBeansRegistered() {
        assertThat(context.getBean(SessionManager.class)).isNotNull();
        assertThat(context.getBean(ChatWebSocketHandler.class)).isNotNull();
    }

    @Test
    void rejectsHandshakeWithInvalidToken() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        CompletableFuture<WebSocketSession> future =
                client.execute(new TextWebSocketHandler() {
                }, wsUrl("invalid-token"));
        assertThatThrownBy(future::join).isInstanceOf(CompletionException.class);
    }

    @Test
    void acceptsValidTokenAndRepliesPong() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.set(message.getPayload());
                latch.countDown();
            }
        }, wsUrl(jwtUtils.generateAccessToken(12345L, 0))).get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("{\"type\":\"PING\"}"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).contains("PONG");
        session.close();
    }

    private String wsUrl(String token) {
        return "ws://localhost:" + port + "/api/ws/chat?token=" + token;
    }
}
