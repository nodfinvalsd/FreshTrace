package com.freshtrace.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知。
 * <p>
 * 由各业务事件的 MQ 消费者统一写入，业务模块不直接依赖通知模块；
 * dedup_key 唯一索引兜底 MQ 重复投递（重复写入触发唯一键冲突并被忽略）。
 */
@TableName("t_notification")
@Data
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {

    /** 接收用户ID */
    private Long userId;

    /** 通知类型，见 {@code NotificationType}：1=订单,2=预售,3=聊天,4=系统 */
    private Integer type;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 关联业务ID（子订单/预售/评价等），供前端跳转 */
    private Long relatedId;

    /** 是否已读 0=否 1=是 */
    private Integer isRead;

    /** 幂等去重键 tag:relatedId:userId */
    private String dedupKey;
}
