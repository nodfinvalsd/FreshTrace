package com.freshtrace.common.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisLuaExecutorTest {

    private static final String INCR_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "local incr = tonumber(ARGV[1]) " +
            "if incr == nil then return -1 end " +
            "current = current + incr " +
            "redis.call('SET', KEYS[1], current) " +
            "return current";

    @Autowired
    private RedisLuaExecutor redisLuaExecutor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final String key = "test:lua:" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        stringRedisTemplate.delete(key);
    }

    @Test
    void missingKeyTreatedAsZero() {
        long result = redisLuaExecutor.executeLong(INCR_SCRIPT, List.of(key), "5");
        assertThat(result).isEqualTo(5L);
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("5");
    }

    @Test
    void accumulateAcrossCalls() {
        redisLuaExecutor.executeLong(INCR_SCRIPT, List.of(key), "5");
        long result = redisLuaExecutor.executeLong(INCR_SCRIPT, List.of(key), "3");
        assertThat(result).isEqualTo(8L);
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("8");
    }

    @Test
    void negativeArgPassedThrough() {
        stringRedisTemplate.opsForValue().set(key, "10");
        long result = redisLuaExecutor.executeLong(INCR_SCRIPT, List.of(key), "-4");
        assertThat(result).isEqualTo(6L);
    }
}
