package com.freshtrace.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 实时重算果农平均评分（V1 决策：AVG 实时聚合，不引入 rating_sum/rating_count 冗余）。
     * <p>
     * ROUND(...,1) 对齐 t_farmer.avg_rating DECIMAL(2,1) 精度；无有效评价时返回 null，
     * 调用方保持 avg_rating 现有默认值（5.0）语义不变。
     */
    @Select("SELECT ROUND(AVG(rating), 1) FROM t_review WHERE farmer_id = #{farmerId} AND deleted = 0")
    BigDecimal selectAvgRating(@Param("farmerId") Long farmerId);
}
