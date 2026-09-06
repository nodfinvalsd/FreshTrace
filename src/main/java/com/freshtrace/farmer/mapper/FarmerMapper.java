package com.freshtrace.farmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.farmer.entity.Farmer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FarmerMapper extends BaseMapper<Farmer> {

    /**
     * 锁定果农行（SELECT ... FOR UPDATE）。
     * <p>
     * 用于串行化同一果农的评分聚合（Phase 4 评价创建：INSERT review → AVG → UPDATE avg_rating），
     * 保证并发评价时最终 avg_rating 与数据库中实际 AVG 一致。
     */
    @Select("SELECT id FROM t_farmer WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);
}
