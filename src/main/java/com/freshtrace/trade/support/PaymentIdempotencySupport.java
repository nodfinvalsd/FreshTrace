package com.freshtrace.trade.support;

import com.freshtrace.common.redis.RedisIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 支付幂等支持（与下单幂等同一套语义，独立 key 前缀）。
 * <p>
 * - 首次请求 tryAcquire 成功 → 执行支付；
 * - 同 token 重复 → 直接返回缓存的首次支付结果；
 * - 业务失败 → release，允许重试；
 * - 成功 → 保留 24h 幂等窗口。
 * <p>
 * 注意：请求幂等（本组件）与支付状态幂等（数据库条件更新）是两层独立防护，
 * 不同 token 的重复支付由后者兜底。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentIdempotencySupport {

    private static final String KEY_PREFIX = "idempotent:payment:";
    private static final String RESULT_SUFFIX = ":result";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisIdempotencyService redisIdempotencyService;
    private final StringRedisTemplate stringRedisTemplate;

    public String keyOf(Long userId, String token) {
        return KEY_PREFIX + userId + ":" + token;
    }

    public boolean tryAcquire(String key, String token) {
        return redisIdempotencyService.tryAcquire(key, token, TTL);
    }

    public void release(String key, String token) {
        try {
            redisIdempotencyService.release(key, token);
        } catch (Exception e) {
            log.warn("payment idempotency key release failed, key={}", key, e);
        }
    }

    public void saveResult(String key, String resultJson) {
        stringRedisTemplate.opsForValue().set(key + RESULT_SUFFIX, resultJson, TTL);
    }

    public String getResult(String key) {
        return stringRedisTemplate.opsForValue().get(key + RESULT_SUFFIX);
    }
}
