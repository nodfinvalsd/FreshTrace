package com.freshtrace.common.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisIdempotencyServiceTest {

    @Autowired
    private RedisIdempotencyService redisIdempotencyService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final String key = "test:idempotent:" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        stringRedisTemplate.delete(key);
    }

    @Test
    void firstAcquireSucceeds() {
        assertThat(redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void secondAcquireWithDifferentTokenFails() {
        redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofMinutes(5));
        assertThat(redisIdempotencyService.tryAcquire(key, "token-b", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void ttlApplied() {
        redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofSeconds(30));
        Long expireSeconds = stringRedisTemplate.getExpire(key);
        assertThat(expireSeconds).isNotNull();
        assertThat(expireSeconds).isGreaterThan(0L);
        assertThat(expireSeconds).isLessThanOrEqualTo(30L);
    }

    @Test
    void ttlExpiredThenAcquireAgain() throws InterruptedException {
        redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofSeconds(1));
        Thread.sleep(1100L);
        assertThat(redisIdempotencyService.tryAcquire(key, "token-b", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void correctTokenReleases() {
        redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofMinutes(5));
        assertThat(redisIdempotencyService.release(key, "token-a")).isTrue();
        assertThat(redisIdempotencyService.tryAcquire(key, "token-b", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void wrongTokenCannotRelease() {
        redisIdempotencyService.tryAcquire(key, "token-a", Duration.ofMinutes(5));
        assertThat(redisIdempotencyService.release(key, "token-b")).isFalse();
        assertThat(redisIdempotencyService.tryAcquire(key, "token-c", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void releaseWhenKeyMissingReturnsFalse() {
        assertThat(redisIdempotencyService.release(key, "token-a")).isFalse();
    }
}
