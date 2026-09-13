package com.freshtrace.admin.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营仪表板数据（Phase 10）：当日/本周/本月核心指标 + 各业务待审核数。
 */
@Data
public class DashboardVO {

    private PeriodMetrics today;

    private PeriodMetrics week;

    private PeriodMetrics month;

    private PendingCounts pending;

    /**
     * 时间区间内的核心运营指标。
     */
    @Data
    public static class PeriodMetrics {

        /** 新增订单量（按创建时间） */
        private Long orderCount;

        /** 交易额（按支付时间的实付金额合计） */
        private BigDecimal tradeAmount;

        /** 新增用户 */
        private Long newUsers;

        /** 新增果农申请 */
        private Long newFarmers;
    }

    /**
     * 各业务待处理数量。
     */
    @Data
    public static class PendingCounts {

        private Long farmerAudits;

        private Long productAudits;

        private Long refunds;

        private Long reports;
    }
}
