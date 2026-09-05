package com.freshtrace.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String ACCESS_PREFIX = "jwt:blacklist:access:";
    private static final String REFRESH_PREFIX = "jwt:blacklist:refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklistAccess(String jti, long ttlSeconds) {
        blacklist(ACCESS_PREFIX, jti, ttlSeconds);
    }

    public void blacklistRefresh(String jti, long ttlSeconds) {
        blacklist(REFRESH_PREFIX, jti, ttlSeconds);
    }

    public boolean isAccessBlacklisted(String jti) {
        return isBlacklisted(ACCESS_PREFIX, jti);
    }

    public boolean isRefreshBlacklisted(String jti) {
        return isBlacklisted(REFRESH_PREFIX, jti);
    }

    private void blacklist(String prefix, String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(prefix + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    private boolean isBlacklisted(String prefix, String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(prefix + jti));
    }
}
