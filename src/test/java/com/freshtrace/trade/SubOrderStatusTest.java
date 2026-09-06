package com.freshtrace.trade;

import com.freshtrace.trade.enums.SubOrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubOrderStatusTest {

    @Test
    void legalTransitions() {
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.PENDING_SHIP)).isTrue();
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.CANCELLED)).isTrue();

        assertThat(SubOrderStatus.PENDING_SHIP.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isTrue();
        assertThat(SubOrderStatus.PENDING_SHIP.canTransitionTo(SubOrderStatus.CANCELLED)).isTrue();
        assertThat(SubOrderStatus.PENDING_SHIP.canTransitionTo(SubOrderStatus.REFUNDING)).isTrue();

        assertThat(SubOrderStatus.PENDING_RECEIVE.canTransitionTo(SubOrderStatus.FINISHED)).isTrue();
        assertThat(SubOrderStatus.PENDING_RECEIVE.canTransitionTo(SubOrderStatus.REFUNDING)).isTrue();

        assertThat(SubOrderStatus.REFUNDING.canTransitionTo(SubOrderStatus.REFUNDED)).isTrue();
        assertThat(SubOrderStatus.REFUNDING.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isTrue();

        assertThat(SubOrderStatus.FINISHED.canTransitionTo(SubOrderStatus.REFUNDING)).isTrue();
    }

    @Test
    void illegalTransitions() {
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.FINISHED)).isFalse();
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.REFUNDING)).isFalse();
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.REFUNDED)).isFalse();

        assertThat(SubOrderStatus.FINISHED.canTransitionTo(SubOrderStatus.CANCELLED)).isFalse();
        assertThat(SubOrderStatus.FINISHED.canTransitionTo(SubOrderStatus.PENDING_SHIP)).isFalse();
        assertThat(SubOrderStatus.FINISHED.canTransitionTo(SubOrderStatus.FINISHED)).isFalse();

        assertThat(SubOrderStatus.CANCELLED.canTransitionTo(SubOrderStatus.PENDING_SHIP)).isFalse();
        assertThat(SubOrderStatus.CANCELLED.canTransitionTo(SubOrderStatus.REFUNDING)).isFalse();
        assertThat(SubOrderStatus.CANCELLED.canTransitionTo(SubOrderStatus.CANCELLED)).isFalse();

        assertThat(SubOrderStatus.REFUNDED.canTransitionTo(SubOrderStatus.FINISHED)).isFalse();
        assertThat(SubOrderStatus.REFUNDED.canTransitionTo(SubOrderStatus.REFUNDING)).isFalse();
        assertThat(SubOrderStatus.REFUNDED.canTransitionTo(SubOrderStatus.REFUNDED)).isFalse();
    }

    @Test
    void sameStateTransitionIsIllegal() {
        for (SubOrderStatus status : SubOrderStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("同状态转换应视为非法: %s", status)
                    .isFalse();
        }
    }

    @Test
    void nullTargetIsIllegal() {
        for (SubOrderStatus status : SubOrderStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void shipTransitionCoverage() {
        // 发货合法转换：仅 PENDING_SHIP → PENDING_RECEIVE
        assertThat(SubOrderStatus.PENDING_SHIP.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isTrue();

        // 发货非法转换：已收货/已完成/已取消/已退款/待支付均不可再发货
        assertThat(SubOrderStatus.PENDING_RECEIVE.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();
        assertThat(SubOrderStatus.FINISHED.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();
        assertThat(SubOrderStatus.CANCELLED.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();
        assertThat(SubOrderStatus.REFUNDED.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();
        assertThat(SubOrderStatus.PENDING_PAY.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isFalse();

        // 注意：REFUNDING → PENDING_RECEIVE 为 Phase 3 退款撤销路径（果农拒绝退款恢复待收货）的合法转换，
        // 发货接口不依赖该转换放行 REFUNDING，而是以精确状态校验 + 条件更新（WHERE status=PENDING_SHIP）双重拦截。
        assertThat(SubOrderStatus.REFUNDING.canTransitionTo(SubOrderStatus.PENDING_RECEIVE)).isTrue();
    }
}
