package com.freshtrace.fulfillment.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.fulfillment.dto.FarmerSubOrderQueryDTO;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.vo.FarmerSubOrderVO;
import com.freshtrace.fulfillment.vo.ReceiveResultVO;
import com.freshtrace.fulfillment.vo.ShipResultVO;

/**
 * 履约服务（Phase 4）：果农发货 + 确认收货。
 * <p>
 * farmerId 由 {@code @FarmerRequired} 切面经 FarmerContext 注入；
 * 确认收货的买家身份由 UserContext 注入，绝不信任客户端参数。
 */
public interface FulfillmentService {

    /**
     * 果农订单列表：仅返回该果农自己的子订单，数据库层按 farmer_id 过滤。
     */
    PageVO<FarmerSubOrderVO> pageFarmerSubOrders(Long farmerId, FarmerSubOrderQueryDTO query);

    /**
     * 发货：PENDING_SHIP → PENDING_RECEIVE（条件更新抢占 + 状态机白名单校验），
     * 成功后联动主订单 PARTIAL_SHIPPED / ALL_SHIPPED。
     */
    ShipResultVO ship(Long farmerId, String subOrderNo, ShipDTO dto);

    /**
     * 买家主动确认收货：仅 PENDING_RECEIVE → FINISHED。
     * 校验买家归属（子订单 → 主订单 → userId）后条件更新抢占，
     * 全部子订单 FINISHED 时联动主订单 FINISHED + finished_at。
     */
    ReceiveResultVO receive(Long userId, String subOrderNo);

    /**
     * 自动确认收货（MQ 延迟消息 / Scheduler 兜底共用入口）。
     * <p>
     * 幂等语义（不抛业务异常，避免 MQ 无意义重试）：
     * - 子订单不存在 → WARN 结束；
     * - 已 FINISHED / 其他非 PENDING_RECEIVE 状态 → INFO 幂等跳过；
     * - PENDING_RECEIVE → 条件更新抢占，竞争失败同样幂等跳过。
     * 基础设施异常（DB 不可用等）照常抛出，交由调用方重试机制处理。
     */
    void autoConfirmSubOrder(String subOrderNo);
}
