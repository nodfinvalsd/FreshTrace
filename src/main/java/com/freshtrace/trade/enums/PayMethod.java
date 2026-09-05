package com.freshtrace.trade.enums;

import lombok.Getter;

/**
 * 支付方式。value 与 DDL 默认值一致，V1 仅模拟支付。
 */
@Getter
public enum PayMethod {

    SIMULATED("SIMULATED", "模拟支付");

    private final String value;
    private final String desc;

    PayMethod(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static PayMethod fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PayMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        return null;
    }
}
