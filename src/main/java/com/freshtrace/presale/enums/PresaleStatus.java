package com.freshtrace.presale.enums;

import lombok.Getter;

/**
 * 预售状态机。合法流转：
 * <ul>
 *     <li>{@code ONGOING -> CLOSED}：果农手动关闭预售；</li>
 *     <li>{@code ONGOING -> ENDED}：presale_end 到期，由到期消息/定时任务推进。</li>
 * </ul>
 * CLOSED 与 ENDED 为终态，不允许再次开启。
 */
@Getter
public enum PresaleStatus {

    CLOSED(0, "已关闭"),
    ONGOING(1, "进行中"),
    ENDED(2, "已结束");

    private final int code;
    private final String desc;

    PresaleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PresaleStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PresaleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
