package com.freshtrace.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.trade.entity.SubOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SubOrderMapper extends BaseMapper<SubOrder> {

    /**
     * 发货条件更新抢占（Phase 4）。
     * <p>
     * 业务状态即并发仲裁条件：WHERE status = expectedStatus 保证同一子订单的并发发货
     * 只有一个请求更新成功（affectedRows = 1），其余 affectedRows = 0，
     * 由调用方重查区分「已发货（重复操作）」与「其他非法状态」。
     * {@code @Version} 字段保留并自增（防后续 updateById 用旧版本覆盖），
     * 但不作为发货状态转换的主要仲裁手段。
     */
    @Update("UPDATE t_sub_order SET status = #{targetStatus}, "
            + "logistics_company = #{logisticsCompany}, "
            + "logistics_no = #{logisticsNo}, "
            + "shipped_at = #{shippedAt}, "
            + "version = version + 1 "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND deleted = 0")
    int ship(@Param("id") Long id,
             @Param("expectedStatus") Integer expectedStatus,
             @Param("targetStatus") Integer targetStatus,
             @Param("logisticsCompany") String logisticsCompany,
             @Param("logisticsNo") String logisticsNo,
             @Param("shippedAt") LocalDateTime shippedAt);

    /**
     * 确认收货条件更新抢占（Phase 4 Day 2）。
     * <p>
     * 与发货同一条并发模型：WHERE status = expectedStatus 保证主动确认 / MQ 自动确认 /
     * Scheduler 兜底三条路径中只有一个请求能完成 PENDING_RECEIVE → FINISHED 转换，
     * 其余 affectedRows = 0，由调用方按各自语义处理（主动→业务异常，自动→幂等跳过）。
     */
    @Update("UPDATE t_sub_order SET status = #{targetStatus}, "
            + "received_at = #{receivedAt}, "
            + "version = version + 1 "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND deleted = 0")
    int receive(@Param("id") Long id,
                @Param("expectedStatus") Integer expectedStatus,
                @Param("targetStatus") Integer targetStatus,
                @Param("receivedAt") LocalDateTime receivedAt);
}
