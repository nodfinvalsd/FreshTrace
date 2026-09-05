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
}
