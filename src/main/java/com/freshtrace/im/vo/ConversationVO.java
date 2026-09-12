package com.freshtrace.im.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图：站在当前登录用户视角，给出「对端」信息与自身未读数。
 * <p>
 * 同一会话在不同视角下 peer* 字段含义不同：
 * 买家视角对端是果农（peerRole=1，peerName=果园名），果农视角对端是买家（peerRole=0，peerName=昵称）。
 */
@Data
public class ConversationVO {

    private Long id;

    /** 来源商品ID（可空） */
    private Long productId;

    private String productTitle;

    private String productImage;

    /** 最后一条消息摘要 */
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    /** 当前视角方的未读数 */
    private Integer unreadCount;

    /** 对端身份 0=买家,1=果农 */
    private Integer peerRole;

    /** 对端登录账号 user_id */
    private Long peerUserId;

    private String peerName;

    private String peerAvatar;

    private LocalDateTime createTime;
}
