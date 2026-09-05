package com.freshtrace.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Lua 统一执行器。
 * <p>
 * 库存扣减等需要原子性的 Redis 操作统一经由此组件执行，
 * 不允许各 Service 自行创建 DefaultRedisScript。
 */
@Component
@RequiredArgsConstructor
public class RedisLuaExecutor {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 执行 Lua 脚本。
     *
     * @param script     Lua 脚本文本
     * @param resultType 返回值类型（如 Long.class / List.class）
     * @param keys       KEYS 参数
     * @param args       ARGV 参数
     * @return 脚本执行结果，由 resultType 决定类型
     */
    public <T> T execute(String script, Class<T> resultType, List<String> keys, Object... args) {
        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(resultType);
        return stringRedisTemplate.execute(redisScript, keys, args);
    }

    /**
     * 执行返回整数的 Lua 脚本（库存扣减等场景），空结果统一归一为 0。
     */
    public long executeLong(String script, List<String> keys, Object... args) {
        Long result = execute(script, Long.class, keys, args);
        return result == null ? 0L : result;
    }
}
