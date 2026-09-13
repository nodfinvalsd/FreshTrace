package com.freshtrace.common.cache;

/**
 * Redis 缓存 Key 统一规范。
 * <p>
 * 各业务模块读写缓存必须经此工具生成 Key，禁止散落硬编码字符串。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /**
     * 果农主页缓存 Key（Phase 4 评价更新 avg_rating 后失效；主页业务 Phase 6 实现）。
     */
    public static String farmerHome(Long farmerId) {
        return "farmer:home:" + farmerId;
    }

    /**
     * 预售预约计数 Key（Phase 7）。Redis INCR 仅作写入限流，权威以
     * {@code COUNT(*) FROM t_presale_reservation} 对账刷新。
     */
    public static String presaleCount(Long presaleId) {
        return "presale:count:" + presaleId;
    }

    /**
     * 管理端运营仪表板缓存 Key（Phase 10）。缓存聚合统计结果，TTL 60s，
     * 另有定时任务刷新兜底，避免高并发下反复全表 COUNT。
     */
    public static String adminDashboardOverview() {
        return "admin:dashboard:overview";
    }

    /**
     * 商品详情缓存 Key（Phase 2.14）。读时回写、TTL 1h，商品编辑/审核/状态变更时删除。
     */
    public static String productDetail(Long productId) {
        return "product:detail:" + productId;
    }

    /**
     * 热门商品 ZSET Key（Phase 2.15）。member=productId，score=销量；
     * 定时任务按销量 Top100 全量重建。
     */
    public static String hotProducts() {
        return "product:hot";
    }
}
