package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.trade.dto.RefundApplyDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 模拟退款：已支付未发货子订单的整笔全额退款。
 * <p>
 * 流程（单事务，全成功或全回滚）：
 * PENDING_SHIP →(条件更新抢占) REFUNDING → REFUNDED + 创建 t_refund（uk_sub_order_id 防重）
 * + 恢复该子订单商品库存（MySQL，数量来自 OrderItem 快照）
 * + 全部子订单已退款/取消时主订单 → CANCELLED（不新增主订单状态）。
 * <p>
 * 幂等：重复退款请求命中 REFUNDED 直接返回既有退款记录，不重复恢复库存；
 * 并发重复由子订单条件更新抢占保证只有一个执行完整流程。
 */
@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final SubOrderMapper subOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundMapper refundMapper;
    private final ProductStockMapper productStockMapper;
    private final StockDeductionSupport stockDeductionSupport;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public RefundServiceImpl(SubOrderMapper subOrderMapper,
                             OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper,
                             RefundMapper refundMapper,
                             ProductStockMapper productStockMapper,
                             StockDeductionSupport stockDeductionSupport,
                             ObjectProvider<RocketMqProducer> rocketMqProducerProvider,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.subOrderMapper = subOrderMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundMapper = refundMapper;
        this.productStockMapper = productStockMapper;
        this.stockDeductionSupport = stockDeductionSupport;
        this.rocketMqProducerProvider = rocketMqProducerProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record StockChange(Long productId, Integer quantity) {
    }

    private record RefundContext(RefundResultVO result, Order order, SubOrder subOrder,
                                 List<StockChange> stockChanges, boolean transitioned) {
    }

    @Override
    public RefundResultVO refund(Long userId, String subOrderNo, RefundApplyDTO dto) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        Order order = orderMapper.selectById(subOrder.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }

        Integer status = subOrder.getStatus();
        if (SubOrderStatus.REFUNDED.getCode() == status) {
            return existingResult(subOrder);
        }
        if (SubOrderStatus.PENDING_SHIP.getCode() != status) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }

        RefundContext context = transactionTemplate.execute(tx -> refundTx(subOrder, order, dto.getReason()));
        if (context.transitioned()) {
            restoreRedisStock(context);
            try {
                sendRefundMessages(context);
            } catch (Exception e) {
                log.error("send refund messages failed, subOrderNo={}", subOrderNo, e);
            }
        }
        return context.result();
    }

    private RefundContext refundTx(SubOrder subOrder, Order order, String reason) {
        // 抢占：PENDING_SHIP → REFUNDING（状态机白名单合法转换）
        int claimRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrder.getId())
                .eq(SubOrder::getStatus, SubOrderStatus.PENDING_SHIP.getCode())
                .set(SubOrder::getStatus, SubOrderStatus.REFUNDING.getCode()));
        if (claimRows == 0) {
            SubOrder latest = subOrderMapper.selectById(subOrder.getId());
            if (latest != null && SubOrderStatus.REFUNDED.getCode() == latest.getStatus()) {
                return new RefundContext(existingResult(latest), order, latest, List.of(), false);
            }
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }

        // V1 模拟退款：创建退款记录并直接置为已退款
        Refund refund = new Refund();
        refund.setRefundNo("R" + IdWorker.getId());
        refund.setSubOrderId(subOrder.getId());
        refund.setFarmerId(subOrder.getFarmerId());
        refund.setUserId(order.getUserId());
        refund.setAmount(subOrder.getAmount());
        refund.setReason(reason);
        refund.setStatus(RefundStatus.REFUNDED.getCode());
        refund.setHandleReason("V1模拟退款");
        refund.setHandledAt(LocalDateTime.now());
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
                    .set(Order::getCancelledAt, LocalDateTime.now()));
            if (rows != 1) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            order.setStatus(OrderStatus.CANCELLED.getCode());
        }

        RefundResultVO vo = new RefundResultVO();
        vo.setRefundNo(refund.getRefundNo());
        vo.setSubOrderNo(subOrder.getSubOrderNo());
        vo.setAmount(refund.getAmount());
        vo.setStatus(refund.getStatus());
        vo.setHandledAt(refund.getHandledAt());
        return new RefundContext(vo, order, subOrder, stockChanges, true);
    }

    private RefundResultVO existingResult(SubOrder subOrder) {
        Refund refund = refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getSubOrderId, subOrder.getId()));
        if (refund == null) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID);
        }
        RefundResultVO vo = new RefundResultVO();
        vo.setRefundNo(refund.getRefundNo());
        vo.setSubOrderNo(subOrder.getSubOrderNo());
        vo.setAmount(refund.getAmount());
        vo.setStatus(refund.getStatus());
        vo.setHandledAt(refund.getHandledAt());
        return vo;
    }

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

    private void sendRefundMessages(RefundContext context) {
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
}
