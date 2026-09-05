package com.freshtrace.trade.service;

import com.freshtrace.trade.vo.CancelOrderVO;

public interface OrderCancelService {

    /**
     * 用户手动取消自己的待支付订单。已支付/已取消等状态按业务语义处理（拒绝或幂等返回）。
     */
    CancelOrderVO cancelByUser(Long userId, String orderNo);

    /**
     * 30min 超时自动取消（ORDER_TIMEOUT Consumer 调用）。天然幂等：
     * 订单不存在 / 已支付 / 已取消 一律 no-op 返回 null，仅 PENDING_PAY 才真正取消。
     */
    CancelOrderVO cancelByTimeout(Long orderId);
}
