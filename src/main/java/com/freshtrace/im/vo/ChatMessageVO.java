package com.freshtrace.im.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息视图：用于 WebSocket 推送帧与历史消息列表。
 */
@Data
public class ChatMessageVO {

    private Long messageId;

    private Long conversationId;

    /** 发送者登录账号 user_id */
    private Long senderId;

    /** 发送者身份 0=买家,1=果农 */
    private Integer senderRole;

    private String content;

    private LocalDateTime createTime;
}
