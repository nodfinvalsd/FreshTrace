package com.freshtrace.trade.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.trade.dto.RefundArbitrateDTO;
import com.freshtrace.trade.dto.RefundHandleDTO;
import com.freshtrace.trade.dto.RefundQueryDTO;
import com.freshtrace.trade.vo.RefundResultVO;
import com.freshtrace.trade.vo.RefundVO;

public interface RefundService {

    /**
     * 买家申请退款。按子订单状态分流：
     * <ul>
     *   <li>待发货：V1 整笔全额模拟退款，直接置为已退款并恢复库存；</li>
     *   <li>待收货 / 已完成（收货后 7 天内）：创建售后工单（待处理），子订单转退款中，等待果农处理/平台仲裁。</li>
     * </ul>
     * 金额取自 SubOrder.amount，不接受客户端金额。业务幂等：重复请求返回既有退款记录。
     */
    RefundResultVO refund(Long userId, String subOrderNo, RefundApplyDTO dto);

    /**
     * 果农同意退款：待处理 → 已退款，子订单 → 已退款（不恢复库存，货已发出/已签收）。
     */
    RefundResultVO farmerApprove(Long farmerId, Long refundId, RefundHandleDTO dto);

    /**
     * 果农拒绝退款：待处理 → 果农拒绝，子订单恢复原状态（待收货/已完成），进入平台仲裁池。
     */
    RefundResultVO farmerReject(Long farmerId, Long refundId, RefundHandleDTO dto);

    /**
     * 管理员仲裁：待处理/果农拒绝 → 同意退款（已退款）或驳回（已驳回），子订单同步。
     */
    RefundResultVO arbitrate(Long adminId, Long refundId, RefundArbitrateDTO dto);

    /**
     * 管理端退款工单列表（分页，可按状态筛选）。
     */
    PageVO<RefundVO> pageForAdmin(RefundQueryDTO query);

    /**
     * 管理端退款工单详情（含凭证图片）。
     */
    RefundVO adminDetail(Long id);
}
