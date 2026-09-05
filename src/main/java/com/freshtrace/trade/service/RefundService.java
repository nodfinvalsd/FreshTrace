package com.freshtrace.trade.service;

import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.trade.vo.RefundResultVO;

public interface RefundService {

    /**
     * V1 模拟退款：仅支持「已支付、待发货」子订单的整笔全额退款。
     * 金额来自 SubOrder.amount，不接受客户端金额。业务幂等：重复请求返回已退款结果，不重复恢复库存。
     */
    RefundResultVO refund(Long userId, String subOrderNo, RefundApplyDTO dto);
}
