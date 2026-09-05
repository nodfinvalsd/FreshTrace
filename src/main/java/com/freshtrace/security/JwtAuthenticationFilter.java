package com.freshtrace.security;

import com.freshtrace.common.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtils.parseToken(token);
                String tokenType = claims.get("tokenType", String.class);
                if (!JwtUtils.TOKEN_TYPE_ACCESS.equals(tokenType)
                        || jwtBlacklistService.isAccessBlacklisted(claims.getId())) {
                    UserContext.clear();
                } else {
                    Long userId = Long.valueOf(claims.getSubject());
                    Integer role = claims.get("role", Integer.class);
                    LoginUser loginUser = new LoginUser(userId, role);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    UserContext.set(loginUser);
                }
            } catch (Exception e) {
                UserContext.clear();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
