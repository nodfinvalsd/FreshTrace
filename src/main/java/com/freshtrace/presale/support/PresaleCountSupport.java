package com.freshtrace.presale.support;

import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.common.redis.RedisLuaExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预售预约计数支持（Phase 7）。
 * <p>
 * Redis 只做写入限流（快速拦截明显满额的请求），MySQL 的
 * {@code t_presale.reservation_count} 条件更新才是最终事实来源。
 * 冷启动：key 不存在时用传入的 DB 计数初始化（原子地随 INCR 脚本完成），
 * 避免 Redis 冷启动导致的错误放行/拒绝。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresaleCountSupport {

    /**
     * 限流 + 计数 Lua（原子）：
     * KEYS[1]=presale:count:{id}，ARGV[1]=maxReservations(0=不限)，ARGV[2]=DB 计数(冷启动兜底)。
     * 返回 -1=已满；否则返回自增后的计数。
     */
    private static final String INCR_IF_AVAILABLE_SCRIPT = """
            local max = tonumber(ARGV[1])
            local dbCount = tonumber(ARGV[2])
            local current = redis.call('GET', KEYS[1])
            if not current then
                current = dbCount
                redis.call('SET', KEYS[1], current)
            end
            current = tonumber(current)
            if max > 0 and current >= max then
                return -1
            end
            return redis.call('INCR', KEYS[1])
            """;

    private final RedisLuaExecutor redisLuaExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    public String keyOf(Long presaleId) {
        return CacheKeys.presaleCount(presaleId);
    }

    /**
     * 原子限流自增。
     *
     * @return -1=预约名额已满；否则为自增后的计数
     */
    public long tryIncrement(Long presaleId, int maxReservations, int dbCount) {
        return redisLuaExecutor.executeLong(INCR_IF_AVAILABLE_SCRIPT, List.of(keyOf(presaleId)),
                String.valueOf(maxReservations), String.valueOf(dbCount));
    }

    /**
     * 补偿回退（MySQL 事务失败时）。失败仅记录 ERROR，由对账任务兜底。
     */
    public void compensate(Long presaleId) {
        try {
            stringRedisTemplate.opsForValue().decrement(keyOf(presaleId));
        } catch (Exception e) {
            log.error("presale count compensate failed, presaleId={}", presaleId, e);
        }
    }

    /**
     * 用 MySQL 权威计数刷新 Redis（对账任务调用）。
     */
    public void sync(Long presaleId, int dbCount) {
        try {
            stringRedisTemplate.opsForValue().set(keyOf(presaleId), String.valueOf(dbCount));
        } catch (Exception e) {
            log.error("presale count sync failed, presaleId={}, dbCount={}", presaleId, dbCount, e);
        }
    }
}
