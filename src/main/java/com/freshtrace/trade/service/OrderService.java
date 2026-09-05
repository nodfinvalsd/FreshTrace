package com.freshtrace.trade.service;

import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.vo.OrderCreateVO;

public interface OrderService {

    /**
     * 创建订单。
     *
     * @param userId           当前登录用户（服务端取自 UserContext，不信任客户端）
     * @param dto              addressId + selectedCartIds + remark
     * @param idempotencyToken 幂等令牌（Idempotency-Key 请求头）
     */
    OrderCreateVO createOrder(Long userId, CreateOrderDTO dto, String idempotencyToken);
}
