package com.freshtrace.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息。
 * <p>
 * sender_id 为发送者登录账号 t_user.id（果农即其 user_id）；
 * sender_role 由服务端根据会话成员关系推导（0=买家,1=果农），不信任客户端。
 */
@TableName("t_chat_message")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends BaseEntity {

    /** 所属会话ID */
    private Long conversationId;

    /** 发送者登录账号 user_id */
    private Long senderId;

    /** 发送者身份 0=买家,1=果农 */
    private Integer senderRole;

    /** 文本内容，最长 1000 */
    private String content;

    /** 是否已读 0=否 1=是（接收方读取后批量置为 1） */
    private Integer isRead;
}
