package com.freshtrace.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话：买家(user_id) 与 果农(farmer_id) 一对一。
 * <p>
 * UNIQUE(user_id, farmer_id) 兜底并发重复创建；product_id 记录来源商品(可空)；
 * unread_user / unread_farmer 为双方未读计数冗余，消息发送时对方 +1，标记已读时本侧清零。
 */
@TableName("t_conversation")
@Data
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseEntity {

    /** 买家登录账号 user_id（会话发起方） */
    private Long userId;

    /** 果农ID（t_farmer.id，非登录账号） */
    private Long farmerId;

    /** 来源商品ID，可从商品详情页发起会话；可空 */
    private Long productId;

    /** 最后一条消息摘要，用于会话列表展示 */
    private String lastMessage;

    /** 最后消息时间，会话列表按此倒序 */
    private LocalDateTime lastMessageAt;

    /** 买家未读数 */
    private Integer unreadUser;

    /** 果农未读数 */
    private Integer unreadFarmer;
}
