package com.freshtrace.admin.service.impl;

import com.freshtrace.admin.mapper.DashboardMetricsMapper;
import com.freshtrace.admin.service.AdminDashboardService;
import com.freshtrace.admin.vo.DashboardVO;
import com.freshtrace.common.cache.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营仪表板实现（Phase 10）。
 * <p>
 * 缓存策略：读时回写（cache-aside），TTL 60s；另有定时任务强制刷新兜底。
 * 聚合结果整体 JSON 缓存在单个 key，避免多次全表 COUNT；缓存异常不阻断业务（降级直查）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final DashboardMetricsMapper metricsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardVO getOverview() {
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(CacheKeys.adminDashboardOverview());
        } catch (Exception e) {
            log.warn("read dashboard cache failed, fallback to query", e);
        }
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardVO.class);
            } catch (Exception e) {
                log.warn("deserialize dashboard cache failed, fallback to query", e);
            }
        }
        DashboardVO overview = computeOverview();
        writeCache(overview);
        return overview;
    }

    @Override
    public void refreshCache() {
        writeCache(computeOverview());
    }

    private DashboardVO computeOverview() {
        LocalDate today = LocalDate.now();
        // 用「周期起止」而非「起点 → now」定义区间：结束边界取下一周期起点，
        // 既覆盖当前周期全部数据，也避免应用与数据库时钟微小偏差导致最新数据被排除
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        DashboardVO vo = new DashboardVO();
        vo.setToday(periodMetrics(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        vo.setWeek(periodMetrics(monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay()));
        vo.setMonth(periodMetrics(firstOfMonth.atStartOfDay(), firstOfMonth.plusMonths(1).atStartOfDay()));

        DashboardVO.PendingCounts pending = new DashboardVO.PendingCounts();
        pending.setFarmerAudits(metricsMapper.countPendingFarmerAudits());
        pending.setProductAudits(metricsMapper.countPendingProductAudits());
        pending.setRefunds(metricsMapper.countPendingRefunds());
        pending.setReports(metricsMapper.countPendingReports());
        vo.setPending(pending);
        return vo;
    }

    private DashboardVO.PeriodMetrics periodMetrics(LocalDateTime start, LocalDateTime end) {
        DashboardVO.PeriodMetrics metrics = new DashboardVO.PeriodMetrics();
        metrics.setOrderCount(metricsMapper.countOrders(start, end));
        metrics.setTradeAmount(metricsMapper.sumTradeAmount(start, end));
        metrics.setNewUsers(metricsMapper.countUsers(start, end));
        metrics.setNewFarmers(metricsMapper.countFarmers(start, end));
        return metrics;
    }

    private void writeCache(DashboardVO overview) {
        try {
            stringRedisTemplate.opsForValue().set(CacheKeys.adminDashboardOverview(),
                    objectMapper.writeValueAsString(overview), CACHE_TTL);
        } catch (Exception e) {
            log.warn("write dashboard cache failed", e);
        }
    }
}
