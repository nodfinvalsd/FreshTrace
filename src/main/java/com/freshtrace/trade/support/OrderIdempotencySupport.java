package com.freshtrace.trade.support;

import com.freshtrace.common.redis.RedisIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 下单幂等支持。
 * <p>
 * 语义：
 * - tryAcquire 成功 → 首次请求，执行下单；
 * - tryAcquire 失败 → 重复请求：若存在历史成功结果（orderNo）直接返回，否则拒绝；
 * - 业务失败 → release，允许客户端用同一 token 重试；
 * - 业务成功 → 不 release，保留 24h 幂等窗口，防止同一 token 重复创建订单。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencySupport {

    private static final String KEY_PREFIX = "idempotent:order:";
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
            log.warn("order idempotency key release failed, key={}", key, e);
        }
    }

    public void saveResult(String key, String orderNo) {
        stringRedisTemplate.opsForValue().set(key + RESULT_SUFFIX, orderNo, TTL);
    }

    public String getResult(String key) {
        return stringRedisTemplate.opsForValue().get(key + RESULT_SUFFIX);
    }
}
