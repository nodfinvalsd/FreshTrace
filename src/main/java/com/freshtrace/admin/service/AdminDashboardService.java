package com.freshtrace.admin.service;

import com.freshtrace.admin.vo.DashboardVO;

/**
 * 运营仪表板服务（Phase 10）。
 */
public interface AdminDashboardService {

    /**
     * 获取仪表板数据：优先读 Redis 缓存（TTL 60s），未命中则实时聚合后回写。
     */
    DashboardVO getOverview();

    /**
     * 强制刷新缓存（由定时任务调用，兜底缓存过期/首次加载）。
     */
    void refreshCache();
}
