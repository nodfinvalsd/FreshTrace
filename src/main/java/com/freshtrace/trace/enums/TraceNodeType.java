package com.freshtrace.trace.enums;

import lombok.Getter;

@Getter
public enum TraceNodeType {

    SOWING(1, "播种"),
    FERTILIZING(2, "施肥"),
    FLOWERING(3, "开花"),
    BAGGING(4, "套袋"),
    RIPENING(5, "成熟"),
    HARVESTING(6, "采摘"),
    SHIPPING(7, "发货");

    private final int code;
    private final String desc;

    TraceNodeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 数据库 code -> Java 枚举。未知 code 返回 null，由调用方决定容错策略。
     */
    public static TraceNodeType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TraceNodeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
