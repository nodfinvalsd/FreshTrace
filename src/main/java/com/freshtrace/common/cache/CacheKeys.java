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
}
