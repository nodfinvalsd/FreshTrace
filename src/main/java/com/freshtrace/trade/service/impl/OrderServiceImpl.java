package com.freshtrace.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.mq.MqTags;
import com.freshtrace.common.mq.MqTopics;
import com.freshtrace.common.mq.RocketMqProducer;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.ProductAttribute;
import com.freshtrace.product.enums.ProductLifecycle;
import com.freshtrace.product.mapper.ProductAttributeMapper;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.Payment;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.PayMethod;
import com.freshtrace.trade.enums.PaymentStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.PaymentMapper;
import com.freshtrace.trade.mapper.ProductStockMapper;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.support.OrderIdempotencySupport;
import com.freshtrace.trade.support.SpecSupport;
import com.freshtrace.trade.support.StockDeductionSupport;
import com.freshtrace.trade.vo.OrderCreateVO;
import com.freshtrace.trade.vo.SubOrderBriefVO;
import com.freshtrace.user.service.AddressService;
import com.freshtrace.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 下单核心。
 * <p>
 * 架构约定（不可更改）：
 * - Redis 负责预扣（Lua 原子批量扣减），MySQL 事务负责最终事实与订单数据原子性；
 * - Redis 与 MySQL 不是同一个事务，MySQL 失败时对 Redis 做回补（补偿失败记 ERROR，对账兜底）；
 * - MQ 只在 MySQL 事务提交成功后发送（afterCommit），发送失败不影响已提交订单；
 * - 任何失败都不能产生半个订单（事务全成功或全回滚）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    /** 30min 超时取消延迟（RocketMQ timer message 毫秒级精度） */
    private static final long TIMEOUT_CANCEL_DELAY_MS = 30 * 60 * 1000L;

    private final ShoppingCartMapper shoppingCartMapper;
    private final ProductMapper productMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final OrderMapper orderMapper;
    private final SubOrderMapper subOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final ProductStockMapper productStockMapper;
    private final AddressService addressService;
    private final SpecSupport specSupport;
    private final StockDeductionSupport stockDeductionSupport;
    private final OrderIdempotencySupport orderIdempotencySupport;
    private final ObjectProvider<RocketMqProducer> rocketMqProducerProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private record CartLine(ShoppingCart cart, Product product,
                            BigDecimal unitPrice, BigDecimal totalPrice, String orderSpecSnapshot) {
    }

    private record StockChange(Long productId, Integer quantity) {
    }

    private record PreparedSubOrder(SubOrder subOrder, List<OrderItem> items) {
    }

    private record PreparedOrder(Order order, List<PreparedSubOrder> subOrders, Payment payment,
                                 List<Long> cartIds, List<StockChange> stockChanges) {
    }

    @Override
    public OrderCreateVO createOrder(Long userId, CreateOrderDTO dto, String idempotencyToken) {
        if (!StringUtils.hasText(idempotencyToken)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少幂等令牌(Idempotency-Key)");
        }
        String idempotencyKey = orderIdempotencySupport.keyOf(userId, idempotencyToken);
        if (!orderIdempotencySupport.tryAcquire(idempotencyKey, idempotencyToken)) {
            String cachedOrderNo = orderIdempotencySupport.getResult(idempotencyKey);
            if (cachedOrderNo != null) {
                return OrderCreateVO.repeat(cachedOrderNo);
            }
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_CONFLICT);
        }

        PreparedOrder prepared;
        try {
            prepared = prepareOrder(userId, dto);
        } catch (RuntimeException e) {
            // 校验失败或 Redis 预扣失败：Lua 原子性保证不足时整批未扣，可直接释放幂等标记允许重试
            orderIdempotencySupport.release(idempotencyKey, idempotencyToken);
            throw e;
        }

        try {
            OrderCreateVO result = transactionTemplate.execute(status -> persist(prepared));
            try {
                orderIdempotencySupport.saveResult(idempotencyKey, result.getOrderNo());
            } catch (Exception e) {
                log.warn("save order idempotency result failed, key={}, orderNo={}",
                        idempotencyKey, result.getOrderNo(), e);
            }
            try {
                sendOrderMessages(prepared, result);
            } catch (Exception e) {
                // MQ 发送失败不影响已提交订单，仅记录 ERROR，由后续兜底机制处理
                log.error("send order messages failed, orderNo={}", result.getOrderNo(), e);
            }
            return result;
        } catch (RuntimeException e) {
            // MySQL 事务失败已回滚，回补 Redis 预扣库存（补偿失败记 ERROR，对账兜底）
            for (StockChange change : prepared.stockChanges()) {
                stockDeductionSupport.compensate(change.productId(), change.quantity(),
                        prepared.order().getOrderNo());
            }
            orderIdempotencySupport.release(idempotencyKey, idempotencyToken);
            log.warn("order create failed and rolled back, userId={}, orderNo={}, reason={}",
                    userId, prepared.order().getOrderNo(), e.getMessage());
            throw e;
        }
    }

    /**
     * 事务外准备：读取 + 校验 + 计价 + 冷启动初始化 + Redis Lua 批量预扣 + 构建订单模型。
     * Redis 预扣是本方法最后一步（成功后不再有可抛异常步骤）。
     */
    private PreparedOrder prepareOrder(Long userId, CreateOrderDTO dto) {
        List<Long> cartIds = dto.getSelectedCartIds().stream().distinct().toList();
        if (cartIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要结算的商品");
        }

        List<ShoppingCart> carts = shoppingCartMapper.selectBatchIds(cartIds);
        if (carts.size() != cartIds.size()) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        for (ShoppingCart cart : carts) {
            if (!cart.getUserId().equals(userId)) {
                throw new BizException(ErrorCode.CART_ITEM_PERMISSION_DENIED);
            }
            if (cart.getQuantity() == null || cart.getQuantity() <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "购物车数量非法");
            }
        }

        List<Long> productIds = carts.stream().map(ShoppingCart::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product == null) {
                throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (!isOnSale(product)) {
                throw new BizException(ErrorCode.PRODUCT_NOT_ON_SALE);
            }
        }
        Map<Long, Map<String, BigDecimal>> attrPriceMaps = loadAttrPrices(new LinkedHashSet<>(productIds));

        AddressVO address = addressService.detail(userId, dto.getAddressId());

        // 计价与规格校验（以数据库当前价格/规格为准，不使用购物车展示价格）
        List<CartLine> lines = new ArrayList<>();
        for (ShoppingCart cart : carts) {
            Product product = productMap.get(cart.getProductId());
            Map<String, BigDecimal> attrPrices = attrPriceMaps.getOrDefault(cart.getProductId(), Map.of());
            SpecSupport.ParsedSpec parsed = specSupport.parse(cart.getSpecSnapshot(), attrPrices);
            BigDecimal unitPrice = product.getPrice().add(parsed.extraPrice());
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(cart.getQuantity()));
            String orderSpecSnapshot = specSupport.toOrderSnapshot(cart.getSpecSnapshot(), attrPrices);
            lines.add(new CartLine(cart, product, unitPrice, totalPrice, orderSpecSnapshot));
        }

        // 同商品多规格合并扣减量 + 冷启动初始化 Redis 库存
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (CartLine line : lines) {
            quantityByProduct.merge(line.product().getId(), line.cart().getQuantity(), Integer::sum);
        }
        List<Long> stockProductIds = new ArrayList<>(quantityByProduct.keySet());
        List<Integer> stockQuantities = stockProductIds.stream().map(quantityByProduct::get).toList();
        for (Long productId : stockProductIds) {
            Product product = productMap.get(productId);
            stockDeductionSupport.initIfAbsent(productId,
                    product.getStock() == null ? 0 : product.getStock());
        }

        // Redis Lua 批量原子预扣
        int deductResult = stockDeductionSupport.deductBatch(stockProductIds, stockQuantities);
        if (deductResult == -2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "库存扣减参数非法");
        }
        if (deductResult == -1) {
            // 极端场景：初始化后 key 又被逐出/过期，重新初始化后重试一次
            for (Long productId : stockProductIds) {
                Product product = productMap.get(productId);
                stockDeductionSupport.initIfAbsent(productId,
                        product.getStock() == null ? 0 : product.getStock());
            }
            deductResult = stockDeductionSupport.deductBatch(stockProductIds, stockQuantities);
        }
        if (deductResult != 1) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        // 构建订单模型（订单号/子订单号/支付流水号均为雪花 ID）
        Order order = new Order();
        order.setOrderNo("O" + IdWorker.getId());
        order.setUserId(userId);
        order.setAddressSnapshot(buildAddressSnapshot(address));
        order.setRemark(dto.getRemark());
        order.setStatus(OrderStatus.PENDING_PAY.getCode());

        Map<Long, List<CartLine>> linesByFarmer = new LinkedHashMap<>();
        for (CartLine line : lines) {
            linesByFarmer.computeIfAbsent(line.product().getFarmerId(), k -> new ArrayList<>()).add(line);
        }
        List<PreparedSubOrder> preparedSubOrders = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, List<CartLine>> entry : linesByFarmer.entrySet()) {
            BigDecimal subAmount = entry.getValue().stream()
                    .map(CartLine::totalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            SubOrder subOrder = new SubOrder();
            subOrder.setSubOrderNo("S" + IdWorker.getId());
            subOrder.setFarmerId(entry.getKey());
            subOrder.setAmount(subAmount);
            subOrder.setStatus(SubOrderStatus.PENDING_PAY.getCode());

            List<OrderItem> items = entry.getValue().stream().map(line -> {
                OrderItem item = new OrderItem();
                item.setProductId(line.product().getId());
                item.setProductTitle(line.product().getTitle());
                item.setProductImage(line.product().getMainImage());
                item.setSpecSnapshot(line.orderSpecSnapshot());
                item.setPrice(line.unitPrice());
                item.setQuantity(line.cart().getQuantity());
                item.setTotalPrice(line.totalPrice());
                return item;
            }).toList();

            preparedSubOrders.add(new PreparedSubOrder(subOrder, items));
            totalAmount = totalAmount.add(subAmount);
        }
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        Payment payment = new Payment();
        payment.setPaymentNo("P" + IdWorker.getId());
        payment.setAmount(totalAmount);
        payment.setPayMethod(PayMethod.SIMULATED.getValue());
        payment.setStatus(PaymentStatus.PENDING.getCode());

        List<StockChange> stockChanges = new ArrayList<>();
        for (int i = 0; i < stockProductIds.size(); i++) {
            stockChanges.add(new StockChange(stockProductIds.get(i), stockQuantities.get(i)));
        }

        return new PreparedOrder(order, preparedSubOrders, payment, cartIds, stockChanges);
    }

    /**
     * 事务内持久化：全成功或全回滚。
     */
    private OrderCreateVO persist(PreparedOrder prepared) {
        Order order = prepared.order();
        orderMapper.insert(order);

        for (PreparedSubOrder preparedSubOrder : prepared.subOrders()) {
            SubOrder subOrder = preparedSubOrder.subOrder();
            subOrder.setOrderId(order.getId());
            subOrderMapper.insert(subOrder);
            for (OrderItem item : preparedSubOrder.items()) {
                item.setSubOrderId(subOrder.getId());
                orderItemMapper.insert(item);
            }
        }

        Payment payment = prepared.payment();
        payment.setOrderId(order.getId());
        paymentMapper.insert(payment);

        shoppingCartMapper.deleteBatchIds(prepared.cartIds());

        for (StockChange change : prepared.stockChanges()) {
            int rows = productStockMapper.deductStock(change.productId(), change.quantity(),
                    ProductLifecycle.ON_SALE.getCode());
            if (rows != 1) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, "库存不足或商品状态已变更，请刷新后重试");
            }
        }

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        vo.setSubOrders(prepared.subOrders().stream().map(ps -> {
            SubOrderBriefVO brief = new SubOrderBriefVO();
            brief.setSubOrderNo(ps.subOrder().getSubOrderNo());
            brief.setFarmerId(ps.subOrder().getFarmerId());
            brief.setAmount(ps.subOrder().getAmount());
            return brief;
        }).toList());
        return vo;
    }

    private void sendOrderMessages(PreparedOrder prepared, OrderCreateVO result) {
        RocketMqProducer producer = rocketMqProducerProvider.getIfAvailable();
        if (producer == null) {
            log.info("rocketmq disabled, skip order messages, orderNo={}", result.getOrderNo());
            return;
        }
        Long orderId = prepared.order().getId();
        String orderNo = result.getOrderNo();

        sendSafely(producer, MqTopics.ORDER_TIMEOUT, MqTags.ORDER_CREATED, orderNo,
                body(Map.of("orderId", orderId, "orderNo", orderNo)),
                TIMEOUT_CANCEL_DELAY_MS, true);

        for (PreparedSubOrder ps : prepared.subOrders()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderId);
            payload.put("orderNo", orderNo);
            payload.put("subOrderId", ps.subOrder().getId());
            payload.put("subOrderNo", ps.subOrder().getSubOrderNo());
            payload.put("farmerId", ps.subOrder().getFarmerId());
            sendSafely(producer, MqTopics.FARMER_NOTIFICATION, MqTags.ORDER_CREATED,
                    ps.subOrder().getSubOrderNo(), body(payload), 0, false);
        }

        for (StockChange change : prepared.stockChanges()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", change.productId());
            payload.put("quantity", change.quantity());
            payload.put("orderId", orderId);
            payload.put("orderNo", orderNo);
            sendSafely(producer, MqTopics.ORDER_STOCK, MqTags.STOCK_CHANGED,
                    orderNo, body(payload), 0, false);
        }

        sendSafely(producer, MqTopics.ES_SYNC, MqTags.ORDER_CREATED, orderNo,
                body(Map.of("orderId", orderId, "orderNo", orderNo)), 0, false);
    }

    private void sendSafely(RocketMqProducer producer, String topic, String tag, String keys,
                            String body, long delayMillis, boolean delayed) {
        try {
            if (delayed) {
                producer.sendDelayed(topic, tag, keys, body, delayMillis);
            } else {
                producer.send(topic, tag, keys, body);
            }
        } catch (Exception e) {
            log.error("order MQ message send failed, topic={}, tag={}, keys={}", topic, tag, keys, e);
        }
    }

    private String body(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MQ message body serialize failed", e);
        }
    }

    private String buildAddressSnapshot(AddressVO address) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("receiverName", address.getReceiverName());
        snapshot.put("receiverPhone", address.getReceiverPhone());
        snapshot.put("province", address.getProvince());
        snapshot.put("city", address.getCity());
        snapshot.put("district", address.getDistrict());
        snapshot.put("detail", address.getDetail());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BIZ_ERROR, "地址快照生成失败");
        }
    }

    private boolean isOnSale(Product product) {
        return product.getLifecycle() != null
                && product.getLifecycle() == ProductLifecycle.ON_SALE.getCode()
                && product.getAuditStatus() != null
                && product.getAuditStatus() == 1;
    }

    private Map<Long, Map<String, BigDecimal>> loadAttrPrices(Set<Long> productIds) {
        Map<Long, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        List<ProductAttribute> attributes = productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>().in(ProductAttribute::getProductId, productIds));
        for (ProductAttribute attribute : attributes) {
            result.computeIfAbsent(attribute.getProductId(), k -> new LinkedHashMap<>())
                    .put(attribute.getAttrName() + "|" + attribute.getAttrValue(),
                            attribute.getExtraPrice() == null ? BigDecimal.ZERO : attribute.getExtraPrice());
        }
        return result;
    }
}
