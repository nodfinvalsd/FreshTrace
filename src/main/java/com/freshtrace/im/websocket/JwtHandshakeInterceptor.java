package com.freshtrace.im.websocket;

import com.freshtrace.common.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器：在握手阶段校验 JWT，非法直接拒绝连接。
 * <p>
 * 浏览器 WebSocket 无法自定义请求头，故 token 通过 query 传入（{@code /ws/chat?token=xxx}）；
 * 校验通过后把 userId / role 写入 session attributes，供 {@link ChatWebSocketHandler} 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** session attributes 中的用户ID键 */
    public static final String ATTR_USER_ID = "userId";

    /** session attributes 中的角色键（0=普通用户,1=管理员；果农身份另行查库判断） */
    public static final String ATTR_ROLE = "role";

    private static final String TOKEN_PARAM = "token";

    private final JwtUtils jwtUtils;

    /**
     * 握手前校验：解析 token → 必须是 access 类型 → 写入身份属性。
     *
     * @return false 表示拒绝握手（Spring 直接返回握手失败，不建立连接）
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("chat handshake rejected: missing token, uri={}", request.getURI());
            return false;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            // 只接受访问令牌，拒绝 refresh token 用于 WebSocket
            if (!JwtUtils.TOKEN_TYPE_ACCESS.equals(claims.get("tokenType", String.class))) {
                log.warn("chat handshake rejected: not access token");
                return false;
            }
            attributes.put(ATTR_USER_ID, Long.valueOf(claims.getSubject()));
            attributes.put(ATTR_ROLE, claims.get("role", Integer.class));
            return true;
        } catch (Exception e) {
            log.warn("chat handshake rejected: invalid token, uri={}", request.getURI());
            return false;
        }
    }

    /**
     * 握手完成后回调，无需额外处理。
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    /**
     * 依次从 query 参数、Authorization 头解析 token（浏览器用 query，非浏览器客户端可用头）。
     */
    private String resolveToken(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst(TOKEN_PARAM);
        if (StringUtils.hasText(token)) {
            return token;
        }
        String header = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
