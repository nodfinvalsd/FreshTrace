package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.Payment;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.PaymentStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.PaymentMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.trade.support.PaymentIdempotencySupport;
import com.freshtrace.trade.vo.PaymentResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟支付。
 * <p>
 * 一致性设计：
 * - 一个事务内 Payment→SUCCESS、Order→PAID、全部 SubOrder→PENDING_SHIP，全成功或全回滚；
 * - 并发防重复：Payment 条件更新（WHERE status=PENDING）抢占，成功者执行状态推进，
 *   失败者以 READ COMMITTED 重读后按幂等语义返回首次结果；
 * - 请求幂等（Redis Idempotency-Key）与状态幂等（数据库条件更新）双层防护，
 *   不同 token 重复支付同样无法重复推进状态；
 * - MQ（ORDER_PAID）仅在事务提交后发送，失败不回滚、不报错，仅记 ERROR。
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final SubOrderMapper subOrderMapper;
    private final PaymentIdempotencySupport paymentIdempotencySupport;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(OrderMapper orderMapper,
                              PaymentMapper paymentMapper,
                              SubOrderMapper subOrderMapper,
                              PaymentIdempotencySupport paymentIdempotencySupport,
                              ObjectProvider<RocketMqProducer> rocketMqProducerProvider,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.subOrderMapper = subOrderMapper;
        this.paymentIdempotencySupport = paymentIdempotencySupport;
        this.rocketMqProducerProvider = rocketMqProducerProvider;
        this.objectMapper = objectMapper;
        // 支付事务使用 READ COMMITTED：并发抢占失败后重读能看到胜者已提交的状态
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record PaidContext(PaymentResultVO result, Order order, List<SubOrder> subOrders,
                               boolean transitioned) {
    }

    @Override
    public PaymentResultVO pay(Long userId, PayOrderDTO dto, String idempotencyToken) {
        if (!StringUtils.hasText(idempotencyToken)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少幂等令牌(Idempotency-Key)");
        }
        String idempotencyKey = paymentIdempotencySupport.keyOf(userId, idempotencyToken);
        if (!paymentIdempotencySupport.tryAcquire(idempotencyKey, idempotencyToken)) {
            String cached = paymentIdempotencySupport.getResult(idempotencyKey);
            if (cached != null) {
                return parseResult(cached);
            }
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_CONFLICT);
        }

        try {
            PaidContext context = transactionTemplate.execute(status -> doPay(userId, dto.getOrderNo()));
            PaymentResultVO result = context.result();
            try {
                paymentIdempotencySupport.saveResult(idempotencyKey, toJson(result));
            } catch (Exception e) {
                log.warn("save payment idempotency result failed, key={}, orderNo={}",
                        idempotencyKey, result.getOrderNo(), e);
            }
            if (context.transitioned()) {
                try {
                    sendPaidMessages(context);
                } catch (Exception e) {
                    // MQ 发送失败不影响已提交支付，仅记 ERROR，由后续兜底处理
                    log.error("send order paid messages failed, orderNo={}", result.getOrderNo(), e);
                }
            }
            return result;
        } catch (RuntimeException e) {
            paymentIdempotencySupport.release(idempotencyKey, idempotencyToken);
            throw e;
        }
    }

    /**
     * 事务内支付。Payment 条件更新抢占并发：只有抢占成功者（rows=1）才执行状态推进。
     */
    private PaidContext doPay(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }

        Integer orderStatus = order.getStatus();
        if (OrderStatus.PAID.getCode() == orderStatus) {
            return alreadyPaidContext(order);
        }
        if (OrderStatus.PENDING_PAY.getCode() != orderStatus) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }

        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId()));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (PaymentStatus.SUCCESS.getCode() == payment.getStatus()) {
            return alreadyPaidContext(order);
        }
        if (PaymentStatus.PENDING.getCode() != payment.getStatus()) {
            throw new BizException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        // 抢占：条件更新，并发场景下只有一个请求能成功
        // 时间截断到秒，保证并发败方从 DB 重读到的 paid_at 与胜方返回值完全一致（DATETIME 无小数秒）
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int paymentRows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, PaymentStatus.PENDING.getCode())
                .set(Payment::getStatus, PaymentStatus.SUCCESS.getCode())
                .set(Payment::getPaidAt, now));
        if (paymentRows == 0) {
            // 并发竞争失败：胜者已支付完成，按幂等语义返回结果（READ COMMITTED 下可读到已提交状态）
            Payment latest = paymentMapper.selectById(payment.getId());
            if (latest != null && PaymentStatus.SUCCESS.getCode() == latest.getStatus()) {
                return alreadyPaidContext(order);
            }
            throw new BizException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.getCode())
                .set(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getPaidAt, now));
        if (orderRows != 1) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }

        List<SubOrder> subOrders = subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getOrderId, order.getId()));
        if (!subOrders.isEmpty()) {
            // 子订单状态机白名单：PENDING_PAY -> PENDING_SHIP（SubOrderStatus 已定义该合法转换）
            int subRows = subOrderMapper.update(null, new LambdaUpdateWrapper<SubOrder>()
                    .in(SubOrder::getId, subOrders.stream().map(SubOrder::getId).toList())
                    .eq(SubOrder::getStatus, SubOrderStatus.PENDING_PAY.getCode())
                    .set(SubOrder::getStatus, SubOrderStatus.PENDING_SHIP.getCode()));
            if (subRows != subOrders.size()) {
                throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID);
            }
        }

        PaymentResultVO vo = new PaymentResultVO();
        vo.setOrderNo(orderNo);
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(PaymentStatus.SUCCESS.getCode());
        vo.setPaidAt(now);
        return new PaidContext(vo, order, subOrders, true);
    }

    private PaidContext alreadyPaidContext(Order order) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId()));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        PaymentResultVO vo = new PaymentResultVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(payment.getStatus());
        vo.setPaidAt(payment.getPaidAt());
        return new PaidContext(vo, order, List.of(), false);
    }

    private void sendPaidMessages(PaidContext context) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip order paid messages, orderNo={}", context.result().getOrderNo());
            return;
        }
        Order order = context.order();
        String orderNo = context.result().getOrderNo();

        for (SubOrder subOrder : context.subOrders()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.getId());
            payload.put("orderNo", orderNo);
            payload.put("subOrderId", subOrder.getId());
            payload.put("subOrderNo", subOrder.getSubOrderNo());
            payload.put("farmerId", subOrder.getFarmerId());
            sendSafely(producer, MqTopics.FARMER_NOTIFICATION, MqTags.ORDER_PAID,
                    subOrder.getSubOrderNo(), body(payload));
        }

        Map<String, Object> esPayload = new LinkedHashMap<>();
        esPayload.put("orderId", order.getId());
        esPayload.put("orderNo", orderNo);
        esPayload.put("userId", order.getUserId());
        esPayload.put("payAmount", context.result().getPayAmount());
        sendSafely(producer, MqTopics.ES_SYNC, MqTags.ORDER_PAID, orderNo, body(esPayload));
    }

    private void sendSafely(RocketMqProducer producer, String topic, String tag, String keys, String body) {
        try {
            producer.send(topic, tag, keys, body);
        } catch (Exception e) {
            log.error("order paid MQ message send failed, topic={}, tag={}, keys={}", topic, tag, keys, e);
        }
    }

    private String body(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MQ message body serialize failed", e);
        }
    }

    private String toJson(PaymentResultVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            throw new IllegalStateException("payment result serialize failed", e);
        }
    }

    private PaymentResultVO parseResult(String json) {
        try {
            return objectMapper.readValue(json, PaymentResultVO.class);
        } catch (Exception e) {
            log.warn("parse cached payment result failed", e);
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_CONFLICT);
        }
    }
}
