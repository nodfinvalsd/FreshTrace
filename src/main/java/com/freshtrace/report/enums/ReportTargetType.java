package com.freshtrace.report.enums;

import lombok.Getter;

/**
 * 举报对象类型。code 与 DDL 注释一致。
 */
@Getter
public enum ReportTargetType {

    TRACE(1, "溯源信息"),
    PRODUCT(2, "商品品质"),
    FARMER(3, "果农行为");

    private final int code;
    private final String desc;

    ReportTargetType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReportTargetType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReportTargetType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
