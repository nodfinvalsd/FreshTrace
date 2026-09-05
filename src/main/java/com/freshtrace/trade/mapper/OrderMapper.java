package com.freshtrace.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
