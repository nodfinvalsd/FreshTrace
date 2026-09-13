package com.freshtrace.admin.scheduler;

import com.freshtrace.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运营仪表板缓存刷新任务（Phase 10）。
 * <p>
 * 默认每 60s 强制刷新缓存，与 TTL 对齐，保证用户访问时命中热点数据、避免缓存同时失效；
 * 通过 admin.dashboard.cache-refresh-enabled 开关控制（测试环境关闭以减少噪声）。
 */
@Component
@ConditionalOnProperty(name = "admin.dashboard.cache-refresh-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DashboardCacheTask {

    private final AdminDashboardService adminDashboardService;

    @Scheduled(fixedDelayString = "${admin.dashboard.refresh-interval-ms:60000}",
            initialDelayString = "${admin.dashboard.refresh-interval-ms:60000}")
    public void refresh() {
        try {
            adminDashboardService.refreshCache();
        } catch (Exception e) {
            log.error("refresh dashboard cache failed", e);
        }
    }
}
