package com.freshtrace.report.enums;

import lombok.Getter;

/**
 * 举报处理状态。code 与 DDL 注释一致。
 */
@Getter
public enum ReportStatus {

    PENDING(0, "待处理"),
    HANDLED(1, "已处理"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String desc;

    ReportStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReportStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReportStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
