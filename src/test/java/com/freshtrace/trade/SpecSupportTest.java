package com.freshtrace.trade;

import com.freshtrace.common.BizException;
import com.freshtrace.trade.support.SpecSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecSupportTest {

    private final SpecSupport specSupport = new SpecSupport(new ObjectMapper());

    private static final Map<String, BigDecimal> ATTR_PRICES = Map.of(
            "规格|5斤装", BigDecimal.ZERO,
            "规格|10斤装", new BigDecimal("30.00"),
            "包装|礼盒装", new BigDecimal("5.00")
    );

    @Test
    void emptySnapshotReturnsEmptyAndZero() {
        SpecSupport.ParsedSpec parsed = specSupport.parse(null, ATTR_PRICES);
        assertThat(parsed.canonicalSnapshot()).isEmpty();
        assertThat(parsed.extraPrice()).isEqualByComparingTo(BigDecimal.ZERO);

        SpecSupport.ParsedSpec parsedBlank = specSupport.parse("  ", ATTR_PRICES);
        assertThat(parsedBlank.canonicalSnapshot()).isEmpty();
        assertThat(parsedBlank.extraPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void validSnapshotNormalizedAndExtraPriceFromDb() {
        // 客户端传入的 extraPrice=99.00 不应被信任
        String input = "[{\"name\":\"规格\",\"value\":\"10斤装\",\"extraPrice\":99.00}]";
        SpecSupport.ParsedSpec parsed = specSupport.parse(input, ATTR_PRICES);
        assertThat(parsed.canonicalSnapshot()).isEqualTo("[{\"name\":\"规格\",\"value\":\"10斤装\"}]");
        assertThat(parsed.extraPrice()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void entriesSortedCanonically() {
        String input = "[{\"name\":\"包装\",\"value\":\"礼盒装\"},{\"name\":\"规格\",\"value\":\"5斤装\"}]";
        SpecSupport.ParsedSpec parsed = specSupport.parse(input, ATTR_PRICES);
        assertThat(parsed.canonicalSnapshot())
                .isEqualTo("[{\"name\":\"包装\",\"value\":\"礼盒装\"},{\"name\":\"规格\",\"value\":\"5斤装\"}]");
        assertThat(parsed.extraPrice()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void invalidJsonThrowsParamError() {
        assertThatThrownBy(() -> specSupport.parse("not-a-json", ATTR_PRICES))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("格式错误");
    }

    @Test
    void unknownAttributeThrowsParamError() {
        assertThatThrownBy(() -> specSupport.parse("[{\"name\":\"规格\",\"value\":\"99斤装\"}]", ATTR_PRICES))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("规格不存在");
    }

    @Test
    void duplicateAttrNameThrowsParamError() {
        String input = "[{\"name\":\"规格\",\"value\":\"5斤装\"},{\"name\":\"规格\",\"value\":\"10斤装\"}]";
        assertThatThrownBy(() -> specSupport.parse(input, ATTR_PRICES))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复属性名");
    }

    @Test
    void blankNameOrValueThrowsParamError() {
        assertThatThrownBy(() -> specSupport.parse("[{\"name\":\"\",\"value\":\"x\"}]", ATTR_PRICES))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> specSupport.parse("[{\"name\":\"规格\"}]", ATTR_PRICES))
                .isInstanceOf(BizException.class);
    }
}
