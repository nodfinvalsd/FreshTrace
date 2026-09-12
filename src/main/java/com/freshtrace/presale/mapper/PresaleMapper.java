package com.freshtrace.presale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.presale.entity.Presale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PresaleMapper extends BaseMapper<Presale> {

    /**
     * 预约计数条件自增（权威闸门，行锁串行化）：
     * 仅当预售进行中，且未超过 max_reservations（0=不限）时才自增，返回受影响行数。
     * MySQL 的 reservation_count 与预约事实表在同一事务内维护，避免并发超卖。
     */
    @Update("""
            UPDATE t_presale
            SET reservation_count = reservation_count + 1
            WHERE id = #{id}
              AND deleted = 0
              AND status = 1
              AND (max_reservations = 0 OR reservation_count < max_reservations)
            """)
    int incrementReservationCountIfAvailable(@Param("id") Long id);
}
