package com.freshtrace;

import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.Payment;
import com.freshtrace.trade.entity.Refund;
import com.freshtrace.trade.entity.ShoppingCart;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.PaymentMapper;
import com.freshtrace.trade.mapper.RefundMapper;
import com.freshtrace.trade.mapper.ShoppingCartMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易 6 张表 Entity <-> DDL 字段映射验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TradeDomainIntegrationTest {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Test
    void shoppingCartRoundtripWithLogicDelete() {
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(1L);
        cart.setProductId(100L);
        cart.setSpecSnapshot("");
        cart.setQuantity(2);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);
        assertThat(cart.getId()).isNotNull();

        ShoppingCart loaded = shoppingCartMapper.selectById(cart.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getProductId()).isEqualTo(100L);
        assertThat(loaded.getSpecSnapshot()).isEmpty();
        assertThat(loaded.getQuantity()).isEqualTo(2);
        assertThat(loaded.getSelected()).isEqualTo(1);

        shoppingCartMapper.deleteById(cart.getId());
        assertThat(shoppingCartMapper.selectById(cart.getId())).isNull();
        assertThat(shoppingCartMapper.selectList(null)).isEmpty();
    }

    @Test
    void orderRoundtripWithVersionOptimisticLock() {
        Order order = new Order();
        order.setOrderNo("O" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setUserId(1L);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("100.00"));
        order.setStatus(0);
        order.setAddressSnapshot("{\"receiverName\":\"张三\"}");
        orderMapper.insert(order);
        assertThat(order.getId()).isNotNull();

        Order loaded = orderMapper.selectById(order.getId());
        assertThat(loaded.getOrderNo()).isEqualTo(order.getOrderNo());
        assertThat(loaded.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(loaded.getPayAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(loaded.getStatus()).isEqualTo(0);
        assertThat(loaded.getVersion()).isZero();

        loaded.setRemark("首次更新");
        assertThat(orderMapper.updateById(loaded)).isEqualTo(1);

        Order stale = orderMapper.selectById(order.getId());
        stale.setVersion(0);
        stale.setRemark("过期版本更新");
        assertThat(orderMapper.updateById(stale)).isZero();
    }

    @Test
    void subOrderRoundtrip() {
        SubOrder subOrder = new SubOrder();
        subOrder.setSubOrderNo("S" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        subOrder.setOrderId(1L);
        subOrder.setFarmerId(2L);
        subOrder.setAmount(new BigDecimal("58.50"));
        subOrder.setStatus(0);
        subOrderMapper.insert(subOrder);
        assertThat(subOrder.getId()).isNotNull();

        SubOrder loaded = subOrderMapper.selectById(subOrder.getId());
        assertThat(loaded.getOrderId()).isEqualTo(1L);
        assertThat(loaded.getFarmerId()).isEqualTo(2L);
        assertThat(loaded.getAmount()).isEqualByComparingTo(new BigDecimal("58.50"));
        assertThat(loaded.getStatus()).isEqualTo(0);
        assertThat(loaded.getLogisticsCompany()).isNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void orderItemRoundtrip() {
        OrderItem item = new OrderItem();
        item.setSubOrderId(1L);
        item.setProductId(100L);
        item.setProductTitle("海南贵妃芒果 5斤装");
        item.setProductImage("http://img/mango.png");
        item.setSpecSnapshot("[{\"name\":\"规格\",\"value\":\"5斤装\",\"extraPrice\":30.00}]");
        item.setPrice(new BigDecimal("10.50"));
        item.setQuantity(2);
        item.setTotalPrice(new BigDecimal("21.00"));
        orderItemMapper.insert(item);
        assertThat(item.getId()).isNotNull();
        assertThat(item.getCreateTime()).isNotNull();

        OrderItem loaded = orderItemMapper.selectById(item.getId());
        assertThat(loaded.getSubOrderId()).isEqualTo(1L);
        assertThat(loaded.getProductId()).isEqualTo(100L);
        assertThat(loaded.getProductTitle()).isEqualTo("海南贵妃芒果 5斤装");
        assertThat(loaded.getPrice()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(loaded.getQuantity()).isEqualTo(2);
        assertThat(loaded.getTotalPrice()).isEqualByComparingTo(new BigDecimal("21.00"));
    }

    @Test
    void paymentRoundtrip() {
        Payment payment = new Payment();
        payment.setPaymentNo("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payment.setOrderId(1L);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPayMethod("SIMULATED");
        payment.setStatus(0);
        paymentMapper.insert(payment);
        assertThat(payment.getId()).isNotNull();

        Payment loaded = paymentMapper.selectById(payment.getId());
        assertThat(loaded.getOrderId()).isEqualTo(1L);
        assertThat(loaded.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(loaded.getPayMethod()).isEqualTo("SIMULATED");
        assertThat(loaded.getStatus()).isEqualTo(0);
        assertThat(loaded.getPaidAt()).isNull();
    }

    @Test
    void refundRoundtrip() {
        Refund refund = new Refund();
        refund.setRefundNo("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        refund.setSubOrderId(1L);
        refund.setFarmerId(2L);
        refund.setUserId(1L);
        refund.setAmount(new BigDecimal("58.50"));
        refund.setReason("取消订单");
        refund.setStatus(0);
        refundMapper.insert(refund);
        assertThat(refund.getId()).isNotNull();

        Refund loaded = refundMapper.selectById(refund.getId());
        assertThat(loaded.getSubOrderId()).isEqualTo(1L);
        assertThat(loaded.getFarmerId()).isEqualTo(2L);
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getAmount()).isEqualByComparingTo(new BigDecimal("58.50"));
        assertThat(loaded.getReason()).isEqualTo("取消订单");
        assertThat(loaded.getStatus()).isEqualTo(0);
        assertThat(loaded.getEvidenceImages()).isNull();
        assertThat(loaded.getHandlerId()).isNull();
    }
}
