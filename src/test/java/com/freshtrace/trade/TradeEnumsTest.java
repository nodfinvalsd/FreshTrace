package com.freshtrace.trade;

import com.freshtrace.trade.enums.OrderStatus;
import com.freshtrace.trade.enums.PayMethod;
import com.freshtrace.trade.enums.PaymentStatus;
import com.freshtrace.trade.enums.SubOrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeEnumsTest {

    @Test
    void orderStatusCodesMatchDdl() {
        assertThat(OrderStatus.PENDING_PAY.getCode()).isEqualTo(0);
        assertThat(OrderStatus.PAID.getCode()).isEqualTo(1);
        assertThat(OrderStatus.PARTIAL_SHIPPED.getCode()).isEqualTo(2);
        assertThat(OrderStatus.ALL_SHIPPED.getCode()).isEqualTo(3);
        assertThat(OrderStatus.FINISHED.getCode()).isEqualTo(4);
        assertThat(OrderStatus.CANCELLED.getCode()).isEqualTo(5);
    }

    @Test
    void orderStatusFromCode() {
        assertThat(OrderStatus.fromCode(0)).isEqualTo(OrderStatus.PENDING_PAY);
        assertThat(OrderStatus.fromCode(5)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.fromCode(null)).isNull();
        assertThat(OrderStatus.fromCode(99)).isNull();
    }

    @Test
    void subOrderStatusCodesMatchDdl() {
        assertThat(SubOrderStatus.PENDING_PAY.getCode()).isEqualTo(0);
        assertThat(SubOrderStatus.PENDING_SHIP.getCode()).isEqualTo(1);
        assertThat(SubOrderStatus.PENDING_RECEIVE.getCode()).isEqualTo(2);
        assertThat(SubOrderStatus.FINISHED.getCode()).isEqualTo(3);
        assertThat(SubOrderStatus.REFUNDING.getCode()).isEqualTo(4);
        assertThat(SubOrderStatus.REFUNDED.getCode()).isEqualTo(5);
        assertThat(SubOrderStatus.CANCELLED.getCode()).isEqualTo(6);
    }

    @Test
    void subOrderStatusFromCode() {
        assertThat(SubOrderStatus.fromCode(0)).isEqualTo(SubOrderStatus.PENDING_PAY);
        assertThat(SubOrderStatus.fromCode(6)).isEqualTo(SubOrderStatus.CANCELLED);
        assertThat(SubOrderStatus.fromCode(null)).isNull();
        assertThat(SubOrderStatus.fromCode(-1)).isNull();
    }

    @Test
    void paymentStatusCodesMatchDdl() {
        assertThat(PaymentStatus.PENDING.getCode()).isEqualTo(0);
        assertThat(PaymentStatus.SUCCESS.getCode()).isEqualTo(1);
        assertThat(PaymentStatus.FAILED.getCode()).isEqualTo(2);
    }

    @Test
    void paymentStatusFromCode() {
        assertThat(PaymentStatus.fromCode(1)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(PaymentStatus.fromCode(null)).isNull();
        assertThat(PaymentStatus.fromCode(9)).isNull();
    }

    @Test
    void payMethodMatchesDdl() {
        assertThat(PayMethod.SIMULATED.getValue()).isEqualTo("SIMULATED");
        assertThat(PayMethod.fromValue("SIMULATED")).isEqualTo(PayMethod.SIMULATED);
        assertThat(PayMethod.fromValue("WECHAT")).isNull();
        assertThat(PayMethod.fromValue(null)).isNull();
    }
}
