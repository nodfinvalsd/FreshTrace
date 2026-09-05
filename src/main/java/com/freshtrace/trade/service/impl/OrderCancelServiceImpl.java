package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.Payment;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.PaymentStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.PaymentMapper;
import com.freshtrace.trade.mapper.ProductStockMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.OrderCancelService;
import com.freshtrace.trade.support.StockDeductionSupport;
import com.freshtrace.trade.vo.CancelOrderVO;
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
 * 订单取消（手动 + 30min 超时）。
 * <p>
 * 并发仲裁：与支付共用同一条路径——以 t_payment 条件更新抢占
 * （PENDING→FAILED 为取消权，PENDING→SUCCESS 为支付权），
 * 两者在 payment 行上串行化，天然互斥，杜绝「支付成功但被取消」或「取消成功但被改回 PAID」。
 * <p>
 * 库存恢复：MySQL 恢复在事务内（数量来自 OrderItem 快照），Redis 恢复在事务提交后
 * （失败记 ERROR，§9 对账兜底）。同一订单只有抢占成功者执行一次恢复，
 * 重复取消/重复 MQ 均无法二次恢复。
 */
@Service
@Slf4j
public class OrderCancelServiceImpl implements OrderCancelService {

    private final OrderMapper orderMapper;
    private final SubOrderMapper subOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final ProductStockMapper productStockMapper;
    private final StockDeductionSupport stockDeductionSupport;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderCancelServiceImpl(OrderMapper orderMapper,
                                  SubOrderMapper subOrderMapper,
                                  OrderItemMapper orderItemMapper,
                                  PaymentMapper paymentMapper,
                                  ProductStockMapper productStockMapper,
                                  StockDeductionSupport stockDeductionSupport,
                                  ObjectProvider<RocketMqProducer> rocketMqProducerProvider,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.subOrderMapper = subOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.paymentMapper = paymentMapper;
        this.productStockMapper = productStockMapper;
        this.stockDeductionSupport = stockDeductionSupport;
        this.rocketMqProducerProvider = rocketMqProducerProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record StockChange(Long productId, Integer quantity) {
    }

    private record CancelContext(Order order, List<StockChange> stockChanges, boolean transitioned) {
    }

    @Override
    public CancelOrderVO cancelByUser(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }
        return doCancel(order, true);
    }

    @Override
    public CancelOrderVO cancelByTimeout(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("ORDER_TIMEOUT order not found, orderId={}", orderId);
            return null;
        }
        return doCancel(order, false);
    }

    private CancelOrderVO doCancel(Order order, boolean manual) {
        Integer current = order.getStatus();
        if (OrderStatus.CANCELLED.getCode() == current) {
            return toVO(order);
        }
        if (OrderStatus.PENDING_PAY.getCode() != current) {
            if (manual) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            return null;
        }

        CancelContext context = transactionTemplate.execute(status -> cancelTx(order, manual));
        if (context == null) {
            return null;
        }
        if (context.transitioned()) {
            restoreRedisStock(context);
            try {
                sendCancelledMessages(context);
            } catch (Exception e) {
                log.error("send order cancelled messages failed, orderNo={}", context.order().getOrderNo(), e);
            }
        }
        return toVO(context.order());
    }

    private CancelContext cancelTx(Order order, boolean manual) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId()));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (PaymentStatus.SUCCESS.getCode() == payment.getStatus()) {
            if (manual) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
            }
            return null;
        }
        if (PaymentStatus.FAILED.getCode() == payment.getStatus()) {
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && OrderStatus.CANCELLED.getCode() == latest.getStatus()) {
                return new CancelContext(latest, List.of(), false);
            }
            throw new BizException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        // 抢占：Payment PENDING → FAILED（与支付抢占 PENDING → SUCCESS 同一条仲裁路径）
        int claimRows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, PaymentStatus.PENDING.getCode())
                .set(Payment::getStatus, PaymentStatus.FAILED.getCode()));
        if (claimRows == 0) {
            Payment latest = paymentMapper.selectById(payment.getId());
            if (latest != null && PaymentStatus.SUCCESS.getCode() == latest.getStatus()) {
                if (manual) {
                    throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
                }
                return null;
            }
            if (latest != null && PaymentStatus.FAILED.getCode() == latest.getStatus()) {
                Order latestOrder = orderMapper.selectById(order.getId());
                if (latestOrder != null && OrderStatus.CANCELLED.getCode() == latestOrder.getStatus()) {
                    return new CancelContext(latestOrder, List.of(), false);
                }
            }
            throw new BizException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.getCode())
                .set(Order::getStatus, OrderStatus.CANCELLED.getCode())
                .set(Order::getCancelledAt, now));
        if (orderRows != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }

        List<SubOrder> subOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, order.getId()));
        if (!subOrders.isEmpty()) {
            int subRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                    .in(SubOrder::getId, subOrders.stream().map(SubOrder::getId).toList())
                    .eq(SubOrder::getStatus, SubOrderStatus.PENDING_PAY.getCode())
                    .set(SubOrder::getStatus, SubOrderStatus.CANCELLED.getCode()));
            if (subRows != subOrders.size()) {
                throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID);
            }
        }

        // 恢复 MySQL 库存：数量来自 OrderItem 快照，不依赖购物车
        List<StockChange> stockChanges = restoreMysqlStock(order.getId());

        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelledAt(now);
        return new CancelContext(order, stockChanges, true);
    }

    private List<StockChange> restoreMysqlStock(Long orderId) {
        List<SubOrder> subOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, orderId));
        List<OrderItem> items = subOrders.isEmpty() ? List.of() : orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getSubOrderId, subOrders.stream().map(SubOrder::getId).toList()));

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

    private void restoreRedisStock(CancelContext context) {
        for (StockChange change : context.stockChanges()) {
            stockDeductionSupport.restore(change.productId(), change.quantity(),
                    context.order().getOrderNo());
        }
    }

    private void sendCancelledMessages(CancelContext context) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip order cancelled messages, orderNo={}",
                    context.order().getOrderNo());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", context.order().getId());
        payload.put("orderNo", context.order().getOrderNo());
        payload.put("userId", context.order().getUserId());
        String body = body(payload);
        sendSafely(producer, MqTopics.ORDER_STOCK, MqTags.ORDER_CANCELLED,
                context.order().getOrderNo(), body);
        sendSafely(producer, MqTopics.ES_SYNC, MqTags.ORDER_CANCELLED,
                context.order().getOrderNo(), body);
    }

    private void sendSafely(RocketMqProducer producer, String topic, String tag, String keys, String body) {
        try {
            producer.send(topic, tag, keys, body);
        } catch (Exception e) {
            log.error("order cancelled MQ message send failed, topic={}, tag={}, keys={}", topic, tag, keys, e);
        }
    }

    private String body(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MQ message body serialize failed", e);
        }
    }

    private CancelOrderVO toVO(Order order) {
        CancelOrderVO vo = new CancelOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setCancelledAt(order.getCancelledAt());
        return vo;
    }
}
