package com.freshtrace.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expire}")
    private long accessExpire;

    @Value("${jwt.refresh-expire}")
    private long refreshExpire;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, Integer role) {
        return buildToken(userId, role, TOKEN_TYPE_ACCESS, accessExpire);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, TOKEN_TYPE_REFRESH, refreshExpire);
    }

    private String buildToken(Long userId, Integer role, String tokenType, long expireSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireSeconds * 1000);
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiry);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key()).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }

    public String getTokenType(String token) {
        return parseToken(token).get("tokenType", String.class);
    }

    public long getAccessExpireSeconds() {
        return accessExpire;
    }

    public long getRefreshExpireSeconds() {
        return refreshExpire;
    }
}
