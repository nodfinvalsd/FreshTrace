package com.freshtrace.im.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册聊天端点并挂载握手鉴权拦截器。
 * <p>
 * 项目 context-path 为 /api，故实际连接地址为 {@code /api/ws/chat?token=xxx}。
 * 跨域放开给所有来源（与 CorsConfig 一致）；鉴权不依赖 Spring Security 过滤链，
 * 由 {@link JwtHandshakeInterceptor} 在握手阶段完成。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * 注册 /ws/chat 端点：绑定处理器与握手拦截器。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
