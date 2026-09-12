package com.freshtrace.presale;

import com.freshtrace.presale.enums.PresaleStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预售状态枚举单元测试（Phase 7 Day 4）。
 */
class PresaleStatusTest {

    @Test
    void fromCodeMapsKnownCodes() {
        assertThat(PresaleStatus.fromCode(0)).isEqualTo(PresaleStatus.CLOSED);
        assertThat(PresaleStatus.fromCode(1)).isEqualTo(PresaleStatus.ONGOING);
        assertThat(PresaleStatus.fromCode(2)).isEqualTo(PresaleStatus.ENDED);
    }

    @Test
    void fromCodeReturnsNullForUnknownOrNull() {
        assertThat(PresaleStatus.fromCode(null)).isNull();
        assertThat(PresaleStatus.fromCode(99)).isNull();
        assertThat(PresaleStatus.fromCode(-1)).isNull();
    }

    @Test
    void codesAndDescAreStable() {
        assertThat(PresaleStatus.CLOSED.getCode()).isZero();
        assertThat(PresaleStatus.ONGOING.getCode()).isEqualTo(1);
        assertThat(PresaleStatus.ENDED.getCode()).isEqualTo(2);
        assertThat(PresaleStatus.ONGOING.getDesc()).isEqualTo("进行中");
    }
}
