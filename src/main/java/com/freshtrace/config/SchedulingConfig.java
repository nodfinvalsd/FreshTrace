package com.freshtrace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度基础配置。
 * <p>
 * Phase 3-8 将在此之上实现：
 * - Redis/MySQL 库存对账（每 5 分钟）
 * - 待支付脏订单扫描
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
