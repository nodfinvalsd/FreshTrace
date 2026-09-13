package com.freshtrace.notification.enums;

import lombok.Getter;

/**
 * 通知类型。code 与 DDL 注释一致。
 * <p>
 * V1 实际生成订单、预售两类；聊天、系统为预留类型（见"待优化功能"）。
 */
@Getter
public enum NotificationType {

    ORDER(1, "订单"),
    PRESALE(2, "预售"),
    CHAT(3, "聊天"),
    SYSTEM(4, "系统");

    private final int code;
    private final String desc;

    NotificationType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static NotificationType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (NotificationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
