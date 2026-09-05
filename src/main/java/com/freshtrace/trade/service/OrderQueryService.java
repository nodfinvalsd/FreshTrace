package com.freshtrace.trade.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.trade.dto.OrderQueryDTO;
import com.freshtrace.trade.vo.OrderDetailVO;
import com.freshtrace.trade.vo.OrderVO;

public interface OrderQueryService {

    /**
     * 买家订单分页列表。用户身份来自服务端 UserContext，查询强制带 user_id 隔离。
     */
    PageVO<OrderVO> page(Long userId, OrderQueryDTO query);

    /**
     * 订单详情。数据库层隔离（orderNo + user_id），他人订单返回 ORDER_NOT_FOUND。
     */
    OrderDetailVO detail(Long userId, String orderNo);
}
