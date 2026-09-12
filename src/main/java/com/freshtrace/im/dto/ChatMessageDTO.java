package com.freshtrace.im.dto;

import lombok.Data;

/**
 * WebSocket 入站聊天帧。
 * <p>
 * 客户端发送 {@code {"type":"CHAT","conversationId":1,"content":"...","clientMsgId":"uuid"}}。
 * 发送者身份、会话归属均由服务端根据登录态与会话关系推导，不接受客户端传入的 senderId / senderRole。
 */
@Data
public class ChatMessageDTO {

    /** 帧类型，固定 CHAT（PING 等其它类型由处理器单独分流） */
    private String type;

    /** 目标会话ID */
    private Long conversationId;

    /** 文本内容 */
    private String content;

    /** 客户端消息去重/回执标识，服务端在 ACK 中原样回传 */
    private String clientMsgId;
}
