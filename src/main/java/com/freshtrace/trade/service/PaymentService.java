package com.freshtrace.trade.service;

import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.vo.PaymentResultVO;

public interface PaymentService {

    /**
     * 模拟支付。金额取自数据库 Order.payAmount，不接受客户端金额。
     *
     * @param userId           当前登录用户（UserContext，不信任客户端）
     * @param dto              orderNo
     * @param idempotencyToken 幂等令牌（Idempotency-Key 请求头）
     */
    PaymentResultVO pay(Long userId, PayOrderDTO dto, String idempotencyToken);
}
