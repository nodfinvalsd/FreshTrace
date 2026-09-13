package com.freshtrace.notification.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.notification.dto.NotificationQueryDTO;
import com.freshtrace.notification.entity.Notification;
import com.freshtrace.notification.enums.NotificationType;
import com.freshtrace.notification.vo.NotificationVO;

import java.util.List;

/**
 * 通知服务（Phase 9）。
 * <p>
 * 写入侧由 {@code NotificationMessageSupport} 在 MQ 消费时调用，dedup_key 唯一索引兜底幂等；
 * 读取侧为用户站内信列表与未读数管理。
 */
public interface NotificationService {

    /**
     * 写入一条通知。dedup_key 冲突（重复消费）时静默忽略。
     */
    void push(Long userId, NotificationType type, String title, String content, Long relatedId, String dedupKey);

    /**
     * 批量写入（预售成熟通知等一对多场景）。逐条幂等，返回实际写入条数。
     */
    int pushBatch(List<Notification> notifications);

    /**
     * 当前用户通知分页，按创建时间倒序；支持 type / 已读状态过滤。
     */
    PageVO<NotificationVO> page(Long userId, NotificationQueryDTO query);

    /**
     * 当前用户未读数。
     */
    long unreadCount(Long userId);

    /**
     * 标记单条已读（校验归属）。
     */
    void markRead(Long userId, Long id);

    /**
     * 全部标记已读。
     */
    void markAllRead(Long userId);
}
