package com.freshtrace.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品库存条件扣减（交易侧专用，避免修改 Phase 2 的 ProductMapper）。
 * <p>
 * 条件扣减：affectedRows = 0 一律视为扣减失败（库存不足或商品状态变更），由调用方事务整体回滚。
 * <p>
 * 设计说明（与规划 SQL 的差异，已确认的冲突点）：
 * 原规划 SQL 带 {@code version = #{version}} 乐观锁校验。实测并发下单时（多个事务同时对同一商品扣库存），
 * 任一事务扣减成功即令 version+1，导致其余并发事务的 version 条件全部失配、大量误回滚，
 * 与"并发不超卖且不误杀"的验收目标矛盾。因此：
 * - 保留 {@code version = version + 1}：若果农用乐观锁并发编辑商品，其 updateById 会因版本变化失败，防止覆盖库存；
 * - 移除 version 条件：订单扣减的准确性由行锁 + 实时 {@code stock >= quantity} 校验保证，不会超卖。
 */
@Mapper
public interface ProductStockMapper {

    @Update("UPDATE t_product SET stock = stock - #{quantity}, version = version + 1 "
            + "WHERE id = #{productId} "
            + "AND stock >= #{quantity} "
            + "AND lifecycle = #{lifecycle} "
            + "AND deleted = 0")
    int deductStock(@Param("productId") Long productId,
                    @Param("quantity") Integer quantity,
                    @Param("lifecycle") Integer lifecycle);

    /**
     * 恢复库存（取消/退款时调用）。
     * <p>
     * 幂等性由调用方保证：仅在"状态抢占成功"的事务内调用一次（订单/子订单条件更新为唯一仲裁点），
     * 重复取消/重复退款不会再次走到这里。affectedRows != 1 视为失败，触发事务回滚。
     */
    @Update("UPDATE t_product SET stock = stock + #{quantity}, version = version + 1 "
            + "WHERE id = #{productId} AND deleted = 0")
    int restoreStock(@Param("productId") Long productId,
                     @Param("quantity") Integer quantity);
}
