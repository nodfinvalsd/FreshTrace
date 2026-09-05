package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.trade.dto.OrderQueryDTO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.Payment;
import com.freshtrace.trade.entity.Refund;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.PaymentStatus;
import com.freshtrace.trade.enums.RefundStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.PaymentMapper;
import com.freshtrace.trade.mapper.RefundMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.OrderQueryService;
import com.freshtrace.trade.vo.OrderDetailVO;
import com.freshtrace.trade.vo.OrderItemVO;
import com.freshtrace.trade.vo.OrderVO;
import com.freshtrace.trade.vo.PaymentBriefVO;
import com.freshtrace.trade.vo.RefundBriefVO;
import com.freshtrace.trade.vo.SubOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单查询（纯读，不开写事务）。
 * <p>
 * 组装策略：分页查主表 → 批量查子订单/明细/退款/支付 → Map 分组 → 内存组装，
 * 避免 JOIN 分页重复行与 N+1。
 * 快照原则：地址展示 address_snapshot，商品展示 OrderItem 快照，金额全部使用落库值，不重新计算。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderMapper orderMapper;
    private final SubOrderMapper subOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageVO<OrderVO> page(Long userId, OrderQueryDTO query) {
        if (query.getStatus() != null && OrderStatus.fromCode(query.getStatus()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单状态参数非法");
        }
        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectPage(page, new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .orderByDesc(Order::getId));
        if (page.getRecords().isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        List<Long> orderIds = page.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<SubOrder>> subOrdersByOrder = loadSubOrdersByOrder(orderIds);
        List<SubOrder> allSubOrders = subOrdersByOrder.values().stream()
                .flatMap(List::stream).toList();
        List<Long> subOrderIds = allSubOrders.stream().map(SubOrder::getId).toList();
        Map<Long, List<OrderItem>> itemsBySubOrder = loadItemsBySubOrder(subOrderIds);
        Map<Long, Refund> refundBySubOrder = loadRefundsBySubOrder(subOrderIds);

        List<OrderVO> records = page.getRecords().stream()
                .map(order -> toOrderVO(order, subOrdersByOrder, itemsBySubOrder, refundBySubOrder))
                .toList();
        return PageVO.of(page, records);
    }

    @Override
    public OrderDetailVO detail(Long userId, String orderNo) {
        // 数据库层隔离：orderNo + user_id 同时匹配，他人订单一律视为不存在
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        Map<Long, List<SubOrder>> subOrdersByOrder = loadSubOrdersByOrder(List.of(order.getId()));
        List<SubOrder> allSubOrders = subOrdersByOrder.getOrDefault(order.getId(), List.of());
        List<Long> subOrderIds = allSubOrders.stream().map(SubOrder::getId).toList();
        Map<Long, List<OrderItem>> itemsBySubOrder = loadItemsBySubOrder(subOrderIds);
        Map<Long, Refund> refundBySubOrder = loadRefundsBySubOrder(subOrderIds);

        OrderVO base = toOrderVO(order, subOrdersByOrder, itemsBySubOrder, refundBySubOrder);
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderNo(base.getOrderNo());
        vo.setStatus(base.getStatus());
        vo.setStatusDesc(base.getStatusDesc());
        vo.setTotalAmount(base.getTotalAmount());
        vo.setPayAmount(base.getPayAmount());
        vo.setCreateTime(base.getCreateTime());
        vo.setPaidAt(base.getPaidAt());
        vo.setCancelledAt(base.getCancelledAt());
        vo.setSubOrders(base.getSubOrders());
        vo.setAddressSnapshot(parseAddressSnapshot(order.getAddressSnapshot()));
        vo.setRemark(order.getRemark());
        vo.setFinishedAt(order.getFinishedAt());

        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId()));
        if (payment != null) {
            PaymentBriefVO paymentVO = new PaymentBriefVO();
            paymentVO.setPaymentNo(payment.getPaymentNo());
            paymentVO.setPayMethod(payment.getPayMethod());
            paymentVO.setStatus(payment.getStatus());
            paymentVO.setStatusDesc(paymentStatusDesc(payment.getStatus()));
            paymentVO.setAmount(payment.getAmount());
            paymentVO.setPaidAt(payment.getPaidAt());
            vo.setPayment(paymentVO);
        }
        return vo;
    }

    private OrderVO toOrderVO(Order order, Map<Long, List<SubOrder>> subOrdersByOrder,
                              Map<Long, List<OrderItem>> itemsBySubOrder,
                              Map<Long, Refund> refundBySubOrder) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(orderStatusDesc(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreateTime(order.getCreateTime());
        vo.setPaidAt(order.getPaidAt());
        vo.setCancelledAt(order.getCancelledAt());
        vo.setSubOrders(subOrdersByOrder.getOrDefault(order.getId(), List.of()).stream()
                .map(sub -> toSubOrderVO(sub, itemsBySubOrder, refundBySubOrder))
                .toList());
        return vo;
    }

    private SubOrderVO toSubOrderVO(SubOrder subOrder, Map<Long, List<OrderItem>> itemsBySubOrder,
                                    Map<Long, Refund> refundBySubOrder) {
        SubOrderVO vo = new SubOrderVO();
        vo.setSubOrderNo(subOrder.getSubOrderNo());
        vo.setFarmerId(subOrder.getFarmerId());
        vo.setStatus(subOrder.getStatus());
        vo.setStatusDesc(subOrderStatusDesc(subOrder.getStatus()));
        vo.setAmount(subOrder.getAmount());
        Refund refund = refundBySubOrder.get(subOrder.getId());
        if (refund != null) {
            RefundBriefVO refundVO = new RefundBriefVO();
            refundVO.setRefundNo(refund.getRefundNo());
            refundVO.setStatus(refund.getStatus());
            refundVO.setStatusDesc(refundStatusDesc(refund.getStatus()));
            refundVO.setAmount(refund.getAmount());
            refundVO.setReason(refund.getReason());
            refundVO.setCreateTime(refund.getCreateTime());
            vo.setRefund(refundVO);
        }
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

    private Map<Long, List<SubOrder>> loadSubOrdersByOrder(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return subOrderMapper.selectList(new LambdaQueryWrapper<SubOrder>()
                        .in(SubOrder::getOrderId, orderIds)
                        .orderByAsc(SubOrder::getId))
                .stream()
                .collect(Collectors.groupingBy(SubOrder::getOrderId, LinkedHashMap::new, Collectors.toList()));
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

    private Map<Long, Refund> loadRefundsBySubOrder(List<Long> subOrderIds) {
        if (subOrderIds.isEmpty()) {
            return Map.of();
        }
        return refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                        .in(Refund::getSubOrderId, subOrderIds))
                .stream()
                .collect(Collectors.toMap(Refund::getSubOrderId, r -> r, (a, b) -> a));
    }

    private Map<String, String> parseAddressSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshot,
                    new tools.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() {
                    });
        } catch (Exception e) {
            log.warn("order address snapshot parse failed", e);
            return Map.of();
        }
    }

    private String orderStatusDesc(Integer code) {
        OrderStatus status = OrderStatus.fromCode(code);
        return status == null ? "" : status.getDesc();
    }

    private String subOrderStatusDesc(Integer code) {
        SubOrderStatus status = SubOrderStatus.fromCode(code);
        return status == null ? "" : status.getDesc();
    }

    private String paymentStatusDesc(Integer code) {
        PaymentStatus status = PaymentStatus.fromCode(code);
        return status == null ? "" : status.getDesc();
    }

    private String refundStatusDesc(Integer code) {
        RefundStatus status = RefundStatus.fromCode(code);
        return status == null ? "" : status.getDesc();
    }
}
