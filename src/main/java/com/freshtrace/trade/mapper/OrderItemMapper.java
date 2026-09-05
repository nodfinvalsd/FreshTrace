package com.freshtrace.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.trade.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
