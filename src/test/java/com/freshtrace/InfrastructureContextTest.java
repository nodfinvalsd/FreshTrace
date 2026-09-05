package com.freshtrace;

import com.freshtrace.common.redis.RedisIdempotencyService;
import com.freshtrace.common.redis.RedisLuaExecutor;
import com.freshtrace.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础设施上下文验证：调度配置生效、Redis 组件注册、测试环境 MQ Producer 默认关闭。
 */
@SpringBootTest
@ActiveProfiles("test")
class InfrastructureContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void schedulingConfigEnabled() {
        assertThat(context.getBean(SchedulingConfig.class)).isNotNull();
    }

    @Test
    void redisInfrastructureBeansRegistered() {
        assertThat(context.getBean(RedisLuaExecutor.class)).isNotNull();
        assertThat(context.getBean(RedisIdempotencyService.class)).isNotNull();
    }

    @Test
    void rocketMqProducerDisabledInTestProfile() {
        assertThat(context.containsBean("rocketMqProducer")).isFalse();
    }
}
