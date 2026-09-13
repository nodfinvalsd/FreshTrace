package com.freshtrace.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运营仪表板聚合查询（Phase 10）。
 * <p>
 * 全部为只读聚合；逻辑删除条件显式书写（原生 @Select 不走 MyBatis-Plus 逻辑删除拦截）。
 * 交易额以已支付订单的实付金额（paid_at 落在统计区间）为准。
 */
@Mapper
public interface DashboardMetricsMapper {

    /** 区间内创建的主订单量 */
    @Select("SELECT COUNT(*) FROM t_order WHERE deleted = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    long countOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 区间内支付的主订单实付金额合计 */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM t_order WHERE deleted = 0 "
            + "AND paid_at IS NOT NULL AND paid_at >= #{start} AND paid_at < #{end}")
    BigDecimal sumTradeAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 区间内新增用户 */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    long countUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 区间内新增果农（认证申请） */
    @Select("SELECT COUNT(*) FROM t_farmer WHERE deleted = 0 "
            + "AND create_time >= #{start} AND create_time < #{end}")
    long countFarmers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 待审核果农认证数 */
    @Select("SELECT COUNT(*) FROM t_farmer WHERE deleted = 0 AND audit_status = 0")
    long countPendingFarmerAudits();

    /** 待审核商品数 */
    @Select("SELECT COUNT(*) FROM t_product WHERE deleted = 0 AND audit_status = 0")
    long countPendingProductAudits();

    /** 待处理/可仲裁退款数（0=待处理,2=果农拒绝） */
    @Select("SELECT COUNT(*) FROM t_refund WHERE deleted = 0 AND status IN (0, 2)")
    long countPendingRefunds();

    /** 待处理举报数 */
    @Select("SELECT COUNT(*) FROM t_report WHERE deleted = 0 AND status = 0")
    long countPendingReports();
}
