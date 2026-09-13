package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.trade.dto.RefundArbitrateDTO;
import com.freshtrace.trade.dto.RefundHandleDTO;
import com.freshtrace.trade.dto.RefundQueryDTO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.Refund;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.RefundStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.ProductStockMapper;
import com.freshtrace.trade.mapper.RefundMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.RefundService;
import com.freshtrace.trade.support.StockDeductionSupport;
import com.freshtrace.trade.vo.RefundResultVO;
import com.freshtrace.trade.vo.RefundVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退款服务（Phase 3 V1 模拟退款 + Phase 10 售后仲裁）。
 * <p>
 * 两类流程，事务边界均为「本地单事务 + 事务提交后发 MQ」：
 * <ol>
 *   <li><b>待发货退款（V1 模拟）</b>：PENDING_SHIP →(条件更新抢占) REFUNDING → REFUNDED，
 *       恢复 MySQL/Redis 库存，全部子订单关闭时主订单 CANCELLED。</li>
 *   <li><b>收货后售后（Phase 10）</b>：PENDING_RECEIVE / FINISHED(收货 7 天内) 申请 → 创建 t_refund
 *       待处理(0)，子订单 → REFUNDING；果农同意 / 平台仲裁同意 → 已退款(4)；拒绝 →
 *       果农拒绝(2)/已驳回(5) 并恢复子订单原状态。货已发出/签收，故售后退款不恢复库存。</li>
 * </ol>
 * 并发仲裁（与支付/取消/发货同一模式）：以业务状态条件更新（WHERE status = ...）抢占，
 * affectedRows=0 视为竞争失败；重复请求命中终态直接返回既有结果，保证幂等。
 */
@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    /** 已完成订单售后窗口：收货后 7 天内可申请 */
    private static final int AFTER_SALE_WINDOW_DAYS = 7;

    private final SubOrderMapper subOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundMapper refundMapper;
    private final ProductStockMapper productStockMapper;
    private final StockDeductionSupport stockDeductionSupport;
    private final UserMapper userMapper;
    private final FarmerMapper farmerMapper;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public RefundServiceImpl(SubOrderMapper subOrderMapper,
                             OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper,
                             RefundMapper refundMapper,
                             ProductStockMapper productStockMapper,
                             StockDeductionSupport stockDeductionSupport,
                             UserMapper userMapper,
                             FarmerMapper farmerMapper,
                             ObjectProvider<RocketMqProducer> rocketMqProducerProvider,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.subOrderMapper = subOrderMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundMapper = refundMapper;
        this.productStockMapper = productStockMapper;
        this.stockDeductionSupport = stockDeductionSupport;
        this.userMapper = userMapper;
        this.farmerMapper = farmerMapper;
        this.rocketMqProducerProvider = rocketMqProducerProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record StockChange(Long productId, Integer quantity) {
    }

    private record RefundContext(RefundResultVO result, Order order, SubOrder subOrder,
                                 Long refundId, List<StockChange> stockChanges, boolean transitioned) {
    }

    // ==================== 买家申请 ====================

    @Override
    public RefundResultVO refund(Long userId, String subOrderNo, RefundApplyDTO dto) {
        SubOrder subOrder = loadSubOrderByNo(subOrderNo);
        Order order = loadOrder(subOrder.getOrderId());
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }

        int status = subOrder.getStatus();
        if (SubOrderStatus.REFUNDED.getCode() == status) {
            // 已退款：幂等返回既有记录
            return existingResult(subOrder);
        }
        if (SubOrderStatus.PENDING_SHIP.getCode() == status) {
            return immediateRefund(subOrder, order, dto.getReason());
        }
        if (SubOrderStatus.PENDING_RECEIVE.getCode() == status
                || SubOrderStatus.FINISHED.getCode() == status) {
            return applyAfterSale(subOrder, order, dto);
        }
        // 待支付 / 退款中 / 已取消 等状态不允许申请
        throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
    }

    /** 待发货：V1 整笔全额模拟退款（立即完成 + 恢复库存 + 主订单联动） */
    private RefundResultVO immediateRefund(SubOrder subOrder, Order order, String reason) {
        RefundContext context = transactionTemplate.execute(tx -> immediateRefundTx(subOrder, order, reason));
        if (context.transitioned()) {
            restoreRedisStock(context);
            sendImmediateRefundMessages(context);
        }
        return context.result();
    }

    private RefundContext immediateRefundTx(SubOrder subOrder, Order order, String reason) {
        // 抢占：PENDING_SHIP → REFUNDING（状态机白名单合法转换）
        int claimRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrder.getId())
                .eq(SubOrder::getStatus, SubOrderStatus.PENDING_SHIP.getCode())
                .set(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode()));
        if (claimRows == 0) {
            SubOrder latest = subOrderMapper.selectById(subOrder.getId());
            if (latest != null && SubOrderStatus.REFUNDED.getCode() == latest.getStatus()) {
                return new RefundContext(existingResult(latest), order, latest, null, List.of(), false);
            }
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }

        // V1 模拟退款：创建退款记录并直接置为已退款
        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setSubOrderId(subOrder.getId());
        refund.setFarmerId(subOrder.getFarmerId());
        refund.setUserId(order.getUserId());
        refund.setAmount(subOrder.getAmount());
        refund.setReason(reason);
        refund.setStatus(RefundStatus.REFUNDED.getCode());
        refund.setHandleReason("V1模拟退款");
        refund.setHandledAt(LocalDateTime.now().withNano(0));
        refundMapper.insert(refund);

        int finishRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrder.getId())
                .eq(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode())
                .set(SubOrder::getStatus, SubOrderStatus.REFUNDED.getCode()));
        if (finishRows != 1) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }

        // 恢复该子订单商品库存（MySQL，未发货全额退款）
        List<StockChange> stockChanges = restoreMysqlStock(subOrder.getId());

        // 全部子订单已退款/取消 → 主订单 CANCELLED（复用现有状态，不新增状态）
        List<SubOrder> allSubOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, order.getId()));
        boolean allClosed = allSubOrders.stream().allMatch(s ->
                SubOrderStatus.REFUNDED.getCode() == s.getStatus()
                        || SubOrderStatus.CANCELLED.getCode() == s.getStatus());
        if (allClosed) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, OrderStatus.PAID.getCode())
                    .set(Order::getStatus, OrderStatus.CANCELLED.getCode())
                    .set(Order::getCancelledAt, LocalDateTime.now().withNano(0)));
            if (rows != 1) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            order.setStatus(OrderStatus.CANCELLED.getCode());
        }
        return new RefundContext(toResult(refund, subOrder), order, subOrder, refund.getId(), stockChanges, true);
    }

    /** 待收货 / 已完成（7 天内）：创建售后工单，等待果农处理/平台仲裁 */
    private RefundResultVO applyAfterSale(SubOrder subOrder, Order order, RefundApplyDTO dto) {
        if (SubOrderStatus.FINISHED.getCode() == subOrder.getStatus()) {
            requireWithinAfterSaleWindow(subOrder);
        }
        RefundContext context = transactionTemplate.execute(tx -> applyAfterSaleTx(subOrder, order, dto));
        if (context.transitioned()) {
            // 通知果农有新售后待处理
            sendRefundNotification(MqTags.REFUND_APPLIED, context, "farmerId", subOrder.getFarmerId());
        }
        return context.result();
    }

    private RefundContext applyAfterSaleTx(SubOrder subOrder, Order order, RefundApplyDTO dto) {
        // 抢占：PENDING_RECEIVE / FINISHED → REFUNDING
        int claimRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrder.getId())
                .eq(SubOrder::getStatus, subOrder.getStatus())
                .set(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode()));
        if (claimRows == 0) {
            SubOrder latest = subOrderMapper.selectById(subOrder.getId());
            if (latest != null && SubOrderStatus.REFUNDING.getCode() == latest.getStatus()) {
                Refund existing = findBySubOrderId(latest.getId());
                if (existing != null) {
                    return new RefundContext(toResult(existing, latest), order, latest,
                            existing.getId(), List.of(), false);
                }
            }
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }

        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setSubOrderId(subOrder.getId());
        refund.setFarmerId(subOrder.getFarmerId());
        refund.setUserId(order.getUserId());
        refund.setAmount(subOrder.getAmount());
        refund.setReason(dto.getReason());
        refund.setEvidenceImages(toJson(dto.getEvidenceImages()));
        refund.setStatus(RefundStatus.PENDING.getCode());
        refundMapper.insert(refund);
        return new RefundContext(toResult(refund, subOrder), order, subOrder, refund.getId(), List.of(), true);
    }

    private void requireWithinAfterSaleWindow(SubOrder subOrder) {
        LocalDateTime receivedAt = subOrder.getReceivedAt();
        if (receivedAt == null
                || receivedAt.isBefore(LocalDateTime.now().minusDays(AFTER_SALE_WINDOW_DAYS))) {
            throw new BizException(ErrorCode.REFUND_AFTER_SALE_EXPIRED);
        }
    }

    // ==================== 果农处理 ====================

    @Override
    public RefundResultVO farmerApprove(Long farmerId, Long refundId, RefundHandleDTO dto) {
        Refund refund = loadRefund(refundId);
        checkFarmerOwner(refund, farmerId);
        if (RefundStatus.REFUNDED.getCode() == refund.getStatus()) {
            return existingResult(refund);
        }
        if (RefundStatus.PENDING.getCode() != refund.getStatus()) {
            throw new BizException(ErrorCode.REFUND_HANDLE_STATUS_INVALID);
        }
        SubOrder subOrder = loadSubOrder(refund.getSubOrderId());
        Order order = loadOrder(subOrder.getOrderId());
        RefundContext context = transactionTemplate.execute(tx -> settleRefundTx(refund, subOrder, order,
                farmerId, reasonOrDefault(dto.getHandleReason(), "果农同意退款"),
                List.of(RefundStatus.PENDING.getCode())));
        if (context.transitioned()) {
            sendRefundNotification(MqTags.REFUND_FARMER_APPROVED, context, "buyerId", order.getUserId());
        }
        return context.result();
    }

    @Override
    public RefundResultVO farmerReject(Long farmerId, Long refundId, RefundHandleDTO dto) {
        Refund refund = loadRefund(refundId);
        checkFarmerOwner(refund, farmerId);
        if (RefundStatus.FARMER_REJECTED.getCode() == refund.getStatus()) {
            return existingResult(refund);
        }
        if (RefundStatus.PENDING.getCode() != refund.getStatus()) {
            throw new BizException(ErrorCode.REFUND_HANDLE_STATUS_INVALID);
        }
        SubOrder subOrder = loadSubOrder(refund.getSubOrderId());
        Order order = loadOrder(subOrder.getOrderId());
        RefundContext context = transactionTemplate.execute(tx -> rejectRefundTx(refund, subOrder, order,
                farmerId, reasonOrDefault(dto.getHandleReason(), "果农拒绝退款"),
                RefundStatus.FARMER_REJECTED.getCode(), List.of(RefundStatus.PENDING.getCode()), false));
        if (context.transitioned()) {
            sendRefundNotification(MqTags.REFUND_FARMER_REJECTED, context, "buyerId", order.getUserId());
        }
        return context.result();
    }

    // ==================== 平台仲裁 ====================

    @Override
    public RefundResultVO arbitrate(Long adminId, Long refundId, RefundArbitrateDTO dto) {
        Refund refund = loadRefund(refundId);
        int status = refund.getStatus();
        if (RefundStatus.REFUNDED.getCode() == status || RefundStatus.REJECTED.getCode() == status) {
            // 已仲裁：幂等返回既有结果
            return existingResult(refund);
        }
        if (status != RefundStatus.PENDING.getCode() && status != RefundStatus.FARMER_REJECTED.getCode()) {
            throw new BizException(ErrorCode.REFUND_HANDLE_STATUS_INVALID);
        }
        SubOrder subOrder = loadSubOrder(refund.getSubOrderId());
        Order order = loadOrder(subOrder.getOrderId());
        // 可仲裁来源：待处理 或 果农拒绝
        List<Integer> sources = List.of(RefundStatus.PENDING.getCode(), RefundStatus.FARMER_REJECTED.getCode());

        RefundContext context;
        if (Boolean.TRUE.equals(dto.getApprove())) {
            context = transactionTemplate.execute(tx -> settleRefundTx(refund, subOrder, order,
                    adminId, reasonOrDefault(dto.getHandleReason(), "平台仲裁：同意退款"), sources));
            if (context.transitioned()) {
                sendRefundNotification(MqTags.REFUND_ARBITRATION_APPROVED, context, "buyerId", order.getUserId());
            }
        } else {
            context = transactionTemplate.execute(tx -> rejectRefundTx(refund, subOrder, order,
                    adminId, reasonOrDefault(dto.getHandleReason(), "平台仲裁：驳回退款"),
                    RefundStatus.REJECTED.getCode(), sources, true));
            if (context.transitioned()) {
                sendRefundNotification(MqTags.REFUND_ARBITRATION_REJECTED, context, "buyerId", order.getUserId());
            }
        }
        return context.result();
    }

    /**
     * 事务核心：同意退款。条件更新抢占 t_refund（来源状态集合 → 已退款）+ 子订单 REFUNDING → REFUNDED。
     * 货已发出/签收，售后退款不恢复库存。
     */
    private RefundContext settleRefundTx(Refund refund, SubOrder subOrder, Order order,
                                         Long handlerId, String handleReason, List<Integer> sourceStatuses) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int refundRows = refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, refund.getId())
                .in(Refund::getStatus, sourceStatuses)
                .set(Refund::getStatus, RefundStatus.REFUNDED.getCode())
                .set(Refund::getHandlerId, handlerId)
                .set(Refund::getHandleReason, handleReason)
                .set(Refund::getHandledAt, now));
        if (refundRows == 0) {
            Refund latest = refundMapper.selectById(refund.getId());
            if (latest != null && RefundStatus.REFUNDED.getCode() == latest.getStatus()) {
                return new RefundContext(toResult(latest, subOrder), order, subOrder,
                        latest.getId(), List.of(), false);
            }
            throw new BizException(ErrorCode.REFUND_HANDLE_STATUS_INVALID);
        }
        int subRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrder.getId())
                .eq(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode())
                .set(SubOrder::getStatus, SubOrderStatus.REFUNDED.getCode()));
        if (subRows != 1) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }
        refund.setStatus(RefundStatus.REFUNDED.getCode());
        refund.setHandlerId(handlerId);
        refund.setHandleReason(handleReason);
        refund.setHandledAt(now);
        return new RefundContext(toResult(refund, subOrder), order, subOrder,
                refund.getId(), List.of(), true);
    }

    /**
     * 事务核心：驳回退款。条件更新抢占 t_refund（来源状态集合 → 目标状态）。
     * <p>
     * {@code revertSubOrder=true}（平台仲裁驳回）时子订单 REFUNDING → 恢复原状态
     * （有收货时间则为已完成，否则待收货）；果农拒绝仅更新退款单，子订单保持 REFUNDING
     * 等待平台仲裁，避免驳回后无法再被仲裁（PRD：果农拒绝 → 平台仲裁 → 已退款/恢复原状态）。
     */
    private RefundContext rejectRefundTx(Refund refund, SubOrder subOrder, Order order,
                                         Long handlerId, String handleReason,
                                         int targetStatus, List<Integer> sourceStatuses, boolean revertSubOrder) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int refundRows = refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, refund.getId())
                .in(Refund::getStatus, sourceStatuses)
                .set(Refund::getStatus, targetStatus)
                .set(Refund::getHandlerId, handlerId)
                .set(Refund::getHandleReason, handleReason)
                .set(Refund::getHandledAt, now));
        if (refundRows == 0) {
            Refund latest = refundMapper.selectById(refund.getId());
            if (latest != null && latest.getStatus() != null && latest.getStatus() == targetStatus) {
                return new RefundContext(toResult(latest, subOrder), order, subOrder,
                        latest.getId(), List.of(), false);
            }
            throw new BizException(ErrorCode.REFUND_HANDLE_STATUS_INVALID);
        }
        if (revertSubOrder) {
            // 仲裁驳回后恢复原子订单状态：签收过 → 已完成，否则 → 待收货
            SubOrderStatus revert = subOrder.getReceivedAt() != null
                    ? SubOrderStatus.FINISHED
                    : SubOrderStatus.PENDING_RECEIVE;
            int subRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                    .eq(SubOrder::getId, subOrder.getId())
                    .eq(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode())
                    .set(SubOrder::getStatus, revert.getCode()));
            if (subRows != 1) {
                throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
            }
        }
        refund.setStatus(targetStatus);
        refund.setHandlerId(handlerId);
        refund.setHandleReason(handleReason);
        refund.setHandledAt(now);
        return new RefundContext(toResult(refund, subOrder), order, subOrder,
                refund.getId(), List.of(), true);
    }

    // ==================== 管理端查询 ====================

    @Override
    public PageVO<RefundVO> pageForAdmin(RefundQueryDTO query) {
        if (query.getStatus() != null && RefundStatus.fromCode(query.getStatus()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款状态参数非法");
        }
        Page<Refund> page = new Page<>(query.getPage(), query.getSize());
        refundMapper.selectPage(page, new LambdaQueryWrapper<Refund>()
                .eq(query.getStatus() != null, Refund::getStatus, query.getStatus())
                .orderByDesc(Refund::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        return PageVO.of(page, assembleRefundVOs(page.getRecords()));
    }

    @Override
    public RefundVO adminDetail(Long id) {
        Refund refund = loadRefund(id);
        return assembleRefundVOs(List.of(refund)).get(0);
    }

    private List<RefundVO> assembleRefundVOs(List<Refund> refunds) {
        // 批量装配子订单 / 主订单 / 买家 / 果农，避免 N+1
        List<Long> subOrderIds = refunds.stream().map(Refund::getSubOrderId).distinct().toList();
        Map<Long, SubOrder> subOrderMap = subOrderIds.isEmpty() ? Map.of()
                : subOrderMapper.selectBatchIds(subOrderIds).stream()
                .collect(Collectors.toMap(SubOrder::getId, Function.identity()));
        List<Long> orderIds = subOrderMap.values().stream().map(SubOrder::getOrderId).distinct().toList();
        Map<Long, Order> orderMap = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Long> userIds = refunds.stream().map(Refund::getUserId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> farmerIds = refunds.stream().map(Refund::getFarmerId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Farmer> farmerMap = farmerIds.isEmpty() ? Map.of()
                : farmerMapper.selectBatchIds(farmerIds).stream()
                .collect(Collectors.toMap(Farmer::getId, Function.identity()));

        return refunds.stream()
                .map(refund -> toRefundVO(refund, subOrderMap, orderMap, userMap, farmerMap))
                .toList();
    }

    private RefundVO toRefundVO(Refund refund, Map<Long, SubOrder> subOrderMap,
                                Map<Long, Order> orderMap, Map<Long, User> userMap,
                                Map<Long, Farmer> farmerMap) {
        SubOrder subOrder = subOrderMap.get(refund.getSubOrderId());
        Order order = subOrder == null ? null : orderMap.get(subOrder.getOrderId());
        User buyer = refund.getUserId() == null ? null : userMap.get(refund.getUserId());
        Farmer farmer = refund.getFarmerId() == null ? null : farmerMap.get(refund.getFarmerId());
        RefundStatus status = RefundStatus.fromCode(refund.getStatus());

        RefundVO vo = new RefundVO();
        vo.setId(refund.getId());
        vo.setRefundNo(refund.getRefundNo());
        vo.setSubOrderId(refund.getSubOrderId());
        vo.setSubOrderNo(subOrder == null ? null : subOrder.getSubOrderNo());
        vo.setOrderId(order == null ? null : order.getId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setFarmerId(refund.getFarmerId());
        vo.setFarmerName(farmer == null ? null : farmer.getRealName());
        vo.setOrchardName(farmer == null ? null : farmer.getOrchardName());
        vo.setUserId(refund.getUserId());
        vo.setBuyerName(buyer == null ? null
                : (buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername()));
        vo.setAmount(refund.getAmount());
        vo.setReason(refund.getReason());
        vo.setEvidenceImages(parseJsonList(refund.getEvidenceImages()));
        vo.setStatus(refund.getStatus());
        vo.setStatusDesc(status == null ? null : status.getDesc());
        vo.setHandlerId(refund.getHandlerId());
        vo.setHandleReason(refund.getHandleReason());
        vo.setHandledAt(refund.getHandledAt());
        vo.setCreateTime(refund.getCreateTime());
        return vo;
    }

    // ==================== 加载与校验 ====================

    private SubOrder loadSubOrderByNo(String subOrderNo) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        return subOrder;
    }

    private SubOrder loadSubOrder(Long subOrderId) {
        SubOrder subOrder = subOrderMapper.selectById(subOrderId);
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        return subOrder;
    }

    private Order loadOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private Refund loadRefund(Long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }

    private Refund findBySubOrderId(Long subOrderId) {
        return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getSubOrderId, subOrderId));
    }

    private void checkFarmerOwner(Refund refund, Long farmerId) {
        if (!refund.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.REFUND_PERMISSION_DENIED);
        }
    }

    private String reasonOrDefault(String reason, String fallback) {
        return (reason == null || reason.isBlank()) ? fallback : reason;
    }

    private String nextRefundNo() {
        return "R" + IdWorker.getId();
    }

    private RefundResultVO existingResult(SubOrder subOrder) {
        Refund refund = findBySubOrderId(subOrder.getId());
        if (refund == null) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }
        return toResult(refund, subOrder);
    }

    private RefundResultVO existingResult(Refund refund) {
        SubOrder subOrder = subOrderMapper.selectById(refund.getSubOrderId());
        return toResult(refund, subOrder);
    }

    private RefundResultVO toResult(Refund refund, SubOrder subOrder) {
        RefundResultVO vo = new RefundResultVO();
        vo.setRefundNo(refund.getRefundNo());
        vo.setSubOrderNo(subOrder == null ? null : subOrder.getSubOrderNo());
        vo.setAmount(refund.getAmount());
        vo.setStatus(refund.getStatus());
        vo.setHandledAt(refund.getHandledAt());
        return vo;
    }

    // ==================== 库存 ====================

    private List<StockChange> restoreMysqlStock(Long subOrderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getSubOrderId, subOrderId));
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        List<StockChange> changes = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            int rows = productStockMapper.restoreStock(entry.getKey(), entry.getValue());
            if (rows != 1) {
                throw new BizException(ErrorCode.BIZ_ERROR, "库存恢复失败，请稍后重试");
            }
            changes.add(new StockChange(entry.getKey(), entry.getValue()));
        }
        return changes;
    }

    private void restoreRedisStock(RefundContext context) {
        for (StockChange change : context.stockChanges()) {
            stockDeductionSupport.restore(change.productId(), change.quantity(),
                    context.order().getOrderNo());
        }
    }

    // ==================== 通知（事务提交后发送） ====================

    /** 待发货退款（V1）：通知果农订单取消/退款 + ES 同步占位 */
    private void sendImmediateRefundMessages(RefundContext context) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip refund messages, subOrderNo={}",
                    context.subOrder().getSubOrderNo());
            return;
        }
        Map<String, Object> farmerPayload = new LinkedHashMap<>();
        farmerPayload.put("orderId", context.order().getId());
        farmerPayload.put("orderNo", context.order().getOrderNo());
        farmerPayload.put("subOrderId", context.subOrder().getId());
        farmerPayload.put("subOrderNo", context.subOrder().getSubOrderNo());
        farmerPayload.put("farmerId", context.subOrder().getFarmerId());
        sendSafely(producer, MqTopics.FARMER_NOTIFICATION, MqTags.ORDER_CANCELLED,
                context.subOrder().getSubOrderNo(), body(farmerPayload));

        Map<String, Object> esPayload = new LinkedHashMap<>();
        esPayload.put("orderId", context.order().getId());
        esPayload.put("orderNo", context.order().getOrderNo());
        esPayload.put("userId", context.order().getUserId());
        sendSafely(producer, MqTopics.ES_SYNC, MqTags.ORDER_CANCELLED,
                context.order().getOrderNo(), body(esPayload));
    }

    /**
     * 发送售后相关通知：receiverField 指明接收方字段（farmerId / buyerId），
     * 由 BusinessNotificationConsumer 统一落库站内信。
     */
    private void sendRefundNotification(String tag, RefundContext context, String receiverField, Long receiverId) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip refund notification, tag={}, subOrderNo={}",
                    tag, context.subOrder().getSubOrderNo());
            return;
        }
        if (receiverId == null) {
            log.warn("refund notification missing receiver, tag={}, subOrderNo={}",
                    tag, context.subOrder().getSubOrderNo());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", context.refundId());
        payload.put("subOrderId", context.subOrder().getId());
        payload.put("subOrderNo", context.subOrder().getSubOrderNo());
        payload.put("orderId", context.order().getId());
        payload.put(receiverField, receiverId);
        sendSafely(producer, MqTopics.NOTIFICATION, tag, context.subOrder().getSubOrderNo(), body(payload));
    }

    private void sendSafely(RocketMqProducer producer, String topic, String tag, String keys, String body) {
        try {
            producer.send(topic, tag, keys, body);
        } catch (Exception e) {
            log.error("refund MQ message send failed, topic={}, tag={}, keys={}", topic, tag, keys, e);
        }
    }

    private String body(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MQ message body serialize failed", e);
        }
    }

    // ==================== JSON ====================

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException("凭证图片序列化失败");
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
