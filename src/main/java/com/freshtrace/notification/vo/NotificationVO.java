package com.freshtrace.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图。
 */
@Data
public class NotificationVO {

    private Long id;

    /** 通知类型 1=订单,2=预售,3=聊天,4=系统 */
    private Integer type;

    /** 类型中文描述，便于前端直接展示 */
    private String typeDesc;

    private String title;

    private String content;

    /** 关联业务ID，供前端跳转 */
    private Long relatedId;

    /** 是否已读 0=否 1=是 */
    private Integer isRead;

    private LocalDateTime createTime;
}
