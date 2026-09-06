package com.freshtrace.fulfillment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.fulfillment.dto.FarmerSubOrderQueryDTO;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.fulfillment.vo.FarmerSubOrderVO;
import com.freshtrace.fulfillment.vo.ReceiveResultVO;
import com.freshtrace.fulfillment.vo.ShipResultVO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.vo.OrderItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 履约实现（Phase 4 Day 1-2：发货 + 确认收货）。
 * <p>
 * 并发仲裁（与 Phase 3 支付/取消同一模式）：
 * - 子订单发货/确认收货均以业务状态条件更新抢占（WHERE status=PENDING_SHIP / PENDING_RECEIVE），
 *   affectedRows=0 视为竞争失败；主动路径抛业务异常，自动路径幂等跳过；
 * - 主订单联动用条件更新保证状态单调推进：
 *   发货方向 PAID → PARTIAL_SHIPPED → ALL_SHIPPED，确认方向 → FINISHED，绝不回退。
 * <p>
 * 事务边界：子订单状态更新 + 主订单联动在同一本地事务（READ COMMITTED）；
 * MQ（7 天自动确认收货延迟消息）仅在发货事务提交后发送，发送失败不影响已发货事实。
 * <p>
 * 三条确认收货路径（买家主动 / MQ 延迟消息 / Scheduler 兜底）最终汇聚到
 * {@link #confirmTx} 统一逻辑，避免状态语义分叉。
 */
@Service
@Slf4j
public class FulfillmentServiceImpl implements FulfillmentService {

    /** 发货后 7 天自动确认收货延迟 */
    private static final long AUTO_CONFIRM_DELAY_MS = 7 * 24 * 60 * 60 * 1000L;

    private final SubOrderMapper subOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public FulfillmentServiceImpl(SubOrderMapper subOrderMapper,
                                  OrderMapper orderMapper,
                                  OrderItemMapper orderItemMapper,
                                  ObjectProvider<RocketMqProducer> rocketMqProducerProvider,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.subOrderMapper = subOrderMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.rocketMqProducerProvider = rocketMqProducerProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record ShipContext(ShipResultVO result, Long orderId, Long subOrderId) {
    }

    private record ConfirmContext(boolean transitioned, SubOrder current, Long orderId, LocalDateTime receivedAt) {
    }

    @Override
    public PageVO<FarmerSubOrderVO> pageFarmerSubOrders(Long farmerId, FarmerSubOrderQueryDTO query) {
        if (query.getStatus() != null && SubOrderStatus.fromCode(query.getStatus()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "子订单状态参数非法");
        }
        // 数据权限：数据库层按 farmer_id 过滤，绝不先查全量再内存过滤
        Page<SubOrder> page = new Page<>(query.getPage(), query.getSize());
        subOrderMapper.selectPage(page, new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getFarmerId, farmerId)
                .eq(query.getStatus() != null, SubOrder::getStatus, query.getStatus())
                .orderByDesc(SubOrder::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }

        List<SubOrder> subOrders = page.getRecords();
        Map<Long, String> orderNoByOrderId = loadOrderNos(subOrders.stream()
                .map(SubOrder::getOrderId).distinct().toList());
        Map<Long, List<OrderItem>> itemsBySubOrder = loadItemsBySubOrder(subOrders.stream()
                .map(SubOrder::getId).toList());

        List<FarmerSubOrderVO> records = subOrders.stream()
                .map(sub -> toFarmerSubOrderVO(sub, orderNoByOrderId, itemsBySubOrder))
                .toList();
        return PageVO.of(page, records);
    }

    @Override
    public ShipResultVO ship(Long farmerId, String subOrderNo, ShipDTO dto) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        if (!subOrder.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.SUB_ORDER_PERMISSION_DENIED);
        }
        // 状态校验（双层）：
        // 1. 精确校验仅 PENDING_SHIP 可发货（REFUNDING 等状态即使 canTransitionTo(PENDING_RECEIVE) 为退款撤销路径，也不允许发货）；
        // 2. 状态机白名单 canTransitionTo 校验 PENDING_SHIP → PENDING_RECEIVE。
        SubOrderStatus current = SubOrderStatus.fromCode(subOrder.getStatus());
        if (current != SubOrderStatus.PENDING_SHIP
                || !current.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)) {
            throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单状态不允许发货");
        }

        ShipContext context = transactionTemplate.execute(status -> shipTx(subOrder, dto));
        try {
            sendAutoConfirmMessage(context);
        } catch (Exception e) {
            log.error("send auto-confirm message failed, subOrderNo={}", subOrderNo, e);
        }
        return context.result();
    }

    /**
     * 事务内：条件更新抢占发货 + 主订单状态联动。
     */
    private ShipContext shipTx(SubOrder subOrder, ShipDTO dto) {
        // 时间截断到秒，与 DATETIME 列精度一致
        LocalDateTime now = LocalDateTime.now().withNano(0);

        int rows = subOrderMapper.ship(subOrder.getId(),
                SubOrderStatus.PENDING_SHIP.getCode(),
                SubOrderStatus.PENDING_RECEIVE.getCode(),
                dto.getLogisticsCompany(), dto.getLogisticsNo(), now);
        if (rows == 0) {
            // 抢占失败：并发发货或状态已变，重读后区分
            SubOrder latest = subOrderMapper.selectById(subOrder.getId());
            if (latest == null) {
                throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
            }
            if (SubOrderStatus.PENDING_RECEIVE.getCode() == latest.getStatus()) {
                throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单已发货，请勿重复操作");
            }
            throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单状态不允许发货");
        }

        syncOrderShippingStatus(subOrder.getOrderId());

        ShipResultVO vo = new ShipResultVO();
        vo.setSubOrderNo(subOrder.getSubOrderNo());
        vo.setStatus(SubOrderStatus.PENDING_RECEIVE.getCode());
        vo.setStatusDesc(SubOrderStatus.PENDING_RECEIVE.getDesc());
        vo.setLogisticsCompany(dto.getLogisticsCompany());
        vo.setLogisticsNo(dto.getLogisticsNo());
        vo.setShippedAt(now);
        return new ShipContext(vo, subOrder.getOrderId(), subOrder.getId());
    }

    /**
     * 主订单状态联动：重新统计该主订单下全部子订单，按 shipped_at 事实字段判定。
     * 全部已发货 → ALL_SHIPPED；部分已发货 → PARTIAL_SHIPPED。
     * <p>
     * 条件更新保证并发安全：仅 PAID / PARTIAL_SHIPPED 允许被联动改写，
     * 两个果农并发发货时状态单调推进，不会把 ALL_SHIPPED 覆盖回 PARTIAL_SHIPPED。
     */
    private void syncOrderShippingStatus(Long orderId) {
        List<SubOrder> subOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, orderId));
        boolean allShipped = !subOrders.isEmpty() && subOrders.stream()
                .allMatch(sub -> sub.getShippedAt() != null);
        OrderStatus target = allShipped ? OrderStatus.ALL_SHIPPED : OrderStatus.PARTIAL_SHIPPED;

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getStatus, List.of(OrderStatus.PAID.getCode(), OrderStatus.PARTIAL_SHIPPED.getCode()))
                .set(Order::getStatus, target.getCode()));
        if (rows == 0) {
            // 并发线程已把主订单推进到 ALL_SHIPPED（或以上）视为正常，其余情况为数据异常
            Order latest = orderMapper.selectById(orderId);
            if (latest == null || OrderStatus.ALL_SHIPPED.getCode() != latest.getStatus()) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "主订单状态异常，发货失败");
            }
        }
    }

    /**
     * 事务提交后：发送 7 天自动确认收货延迟消息（AUTO_CONFIRM / ORDER_SHIPPED，
     * 由 AutoConfirmConsumer 消费，AutoConfirmTask 定时扫描兜底）。
     * 发送失败不影响已发货事实，仅记 ERROR。
     */
    private void sendAutoConfirmMessage(ShipContext context) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip auto-confirm message, subOrderNo={}",
                    context.result().getSubOrderNo());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subOrderId", context.subOrderId());
        payload.put("subOrderNo", context.result().getSubOrderNo());
        payload.put("orderId", context.orderId());
        try {
            producer.sendDelayed(MqTopics.AUTO_CONFIRM, MqTags.ORDER_SHIPPED,
                    context.result().getSubOrderNo(), objectMapper.writeValueAsString(payload),
                    AUTO_CONFIRM_DELAY_MS);
        } catch (Exception e) {
            log.error("auto-confirm message send failed, subOrderNo={}",
                    context.result().getSubOrderNo(), e);
        }
    }

    @Override
    public ReceiveResultVO receive(Long userId, String subOrderNo) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        // 买家归属校验：子订单 → 主订单 → userId，绝不信任客户端参数
        Order order = orderMapper.selectById(subOrder.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }
        // 精确状态校验：仅 PENDING_RECEIVE 可确认（REFUNDING 等状态即使状态机存在
        // FINISHED 方向的其他合法路径，也不允许通过确认收货接口）
        SubOrderStatus current = SubOrderStatus.fromCode(subOrder.getStatus());
        if (current != SubOrderStatus.PENDING_RECEIVE
                || !current.canTransitionTo(SubOrderStatus.FINISHED)) {
            throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单状态不允许确认收货");
        }

        ConfirmContext context = transactionTemplate.execute(status -> confirmTx(subOrder));
        if (!context.transitioned()) {
            SubOrder latest = context.current();
            if (latest != null
                    && SubOrderStatus.FINISHED.getCode() == latest.getStatus()) {
                throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单已确认收货，请勿重复操作");
            }
            throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单状态不允许确认收货");
        }

        ReceiveResultVO vo = new ReceiveResultVO();
        vo.setSubOrderNo(subOrderNo);
        vo.setStatus(SubOrderStatus.FINISHED.getCode());
        vo.setStatusDesc(SubOrderStatus.FINISHED.getDesc());
        vo.setReceivedAt(context.receivedAt());
        return vo;
    }

    @Override
    public void autoConfirmSubOrder(String subOrderNo) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            log.warn("auto confirm: sub order not found, subOrderNo={}", subOrderNo);
            return;
        }
        Integer currentStatus = subOrder.getStatus();
        if (SubOrderStatus.FINISHED.getCode() == currentStatus) {
            log.info("auto confirm: already finished, idempotent skip, subOrderNo={}", subOrderNo);
            return;
        }
        if (SubOrderStatus.PENDING_RECEIVE.getCode() != currentStatus) {
            log.info("auto confirm: not pending receive, skip, subOrderNo={}, status={}",
                    subOrderNo, currentStatus);
            return;
        }

        ConfirmContext context = transactionTemplate.execute(status -> confirmTx(subOrder));
        if (!context.transitioned()) {
            // 并发竞争失败（如与买家主动确认/MQ 重投/Scheduler 撞车）：幂等跳过
            SubOrder latest = context.current();
            log.info("auto confirm: concurrent confirm lost, subOrderNo={}, latestStatus={}",
                    subOrderNo, latest == null ? null : latest.getStatus());
        }
    }

    /**
     * 确认收货统一事务核心：条件更新抢占 PENDING_RECEIVE → FINISHED + 主订单 FINISHED 联动。
     * 三条路径（买家主动 / MQ / Scheduler）共用，禁止各自实现状态变更。
     */
    private ConfirmContext confirmTx(SubOrder subOrder) {
        // 时间截断到秒，与 DATETIME 列精度一致
        LocalDateTime now = LocalDateTime.now().withNano(0);

        int rows = subOrderMapper.receive(subOrder.getId(),
                SubOrderStatus.PENDING_RECEIVE.getCode(),
                SubOrderStatus.FINISHED.getCode(),
                now);
        if (rows == 0) {
            SubOrder latest = subOrderMapper.selectById(subOrder.getId());
            return new ConfirmContext(false, latest, subOrder.getOrderId(), null);
        }

        syncOrderFinishedStatus(subOrder.getOrderId(), now);
        return new ConfirmContext(true, subOrder, subOrder.getOrderId(), now);
    }

    /**
     * 主订单完成联动：全部子订单 FINISHED → 主订单 FINISHED + finished_at；否则主订单保持不动。
     * <p>
     * 条件更新保证并发安全：仅 PAID / PARTIAL_SHIPPED / ALL_SHIPPED 允许被联动推进到 FINISHED，
     * 已 FINISHED 的主订单不会被再次改写（防止并发确认导致 finished_at 被覆盖或状态回退）。
     */
    private void syncOrderFinishedStatus(Long orderId, LocalDateTime now) {
        List<SubOrder> subOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, orderId));
        boolean allFinished = !subOrders.isEmpty() && subOrders.stream()
                .allMatch(sub -> SubOrderStatus.FINISHED.getCode() == sub.getStatus());
        if (!allFinished) {
            return;
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getStatus, List.of(OrderStatus.PAID.getCode(),
                        OrderStatus.PARTIAL_SHIPPED.getCode(), OrderStatus.ALL_SHIPPED.getCode()))
                .set(Order::getStatus, OrderStatus.FINISHED.getCode())
                .set(Order::getFinishedAt, now));
        if (rows == 0) {
            // 并发线程已把主订单推进到 FINISHED 视为正常，其余情况为数据异常
            Order latest = orderMapper.selectById(orderId);
            if (latest == null || OrderStatus.FINISHED.getCode() != latest.getStatus()) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "主订单状态异常，确认收货失败");
            }
        }
    }

    private Map<Long, String> loadOrderNos(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .in(Order::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo));
    }

    private Map<Long, List<OrderItem>> loadItemsBySubOrder(List<Long> subOrderIds) {
        if (subOrderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getSubOrderId, subOrderIds)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getSubOrderId, LinkedHashMap::new, Collectors.toList()));
    }

    private FarmerSubOrderVO toFarmerSubOrderVO(SubOrder subOrder, Map<Long, String> orderNoByOrderId,
                                                Map<Long, List<OrderItem>> itemsBySubOrder) {
        SubOrderStatus status = SubOrderStatus.fromCode(subOrder.getStatus());
        FarmerSubOrderVO vo = new FarmerSubOrderVO();
        vo.setSubOrderNo(subOrder.getSubOrderNo());
        vo.setOrderNo(orderNoByOrderId.getOrDefault(subOrder.getOrderId(), ""));
        vo.setStatus(subOrder.getStatus());
        vo.setStatusDesc(status == null ? "" : status.getDesc());
        vo.setAmount(subOrder.getAmount());
        vo.setLogisticsCompany(subOrder.getLogisticsCompany());
        vo.setLogisticsNo(subOrder.getLogisticsNo());
        vo.setShippedAt(subOrder.getShippedAt());
        vo.setReceivedAt(subOrder.getReceivedAt());
        vo.setCreateTime(subOrder.getCreateTime());
        vo.setItems(itemsBySubOrder.getOrDefault(subOrder.getId(), List.of()).stream()
                .map(this::toOrderItemVO)
                .toList());
        return vo;
    }

    private OrderItemVO toOrderItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setProductTitle(item.getProductTitle());
        vo.setProductImage(item.getProductImage());
        vo.setSpecSnapshot(item.getSpecSnapshot());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setTotalPrice(item.getTotalPrice());
        return vo;
    }
}
