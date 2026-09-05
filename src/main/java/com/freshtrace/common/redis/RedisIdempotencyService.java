package com.freshtrace.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 幂等组件（SETNX + 带 token 校验的安全释放）。
 * <p>
 * 用于下单防重复提交等场景：业务方先获取幂等 token，
 * 同一 key 在同一 token 生命周期内只允许一个请求继续执行。
 * <p>
 * 注意：本组件是"幂等标记"，不是分布式锁——持有者自身可重复操作，互斥语义由业务保证。
 * 释放时通过 Lua 比对 value，避免误删其他请求的标记。
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisLuaExecutor redisLuaExecutor;

    /**
     * 尝试获取幂等标记。
     *
     * @param key   幂等 key（建议带业务前缀，如 idempotent:order:create:{userId}:{requestId}）
     * @param token 本次请求的唯一 token（通常为 UUID）
     * @param ttl   标记有效期，超时自动失效（兜底，防止异常情况下标记永不释放）
     * @return true=获取成功（可继续执行业务）；false=已存在（视为重复请求）
     */
    public boolean tryAcquire(String key, String token, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl));
    }

    /**
     * 释放幂等标记。仅当 value 与本次请求的 token 一致时才会删除。
     *
     * @return true=释放成功；false=标记不存在或已被其他请求持有（不允许误删）
     */
    public boolean release(String key, String token) {
        long deleted = redisLuaExecutor.executeLong(RELEASE_SCRIPT, List.of(key), token);
        return deleted == 1L;
    }
}
