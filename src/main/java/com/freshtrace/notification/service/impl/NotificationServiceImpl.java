package com.freshtrace.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.notification.dto.NotificationQueryDTO;
import com.freshtrace.notification.entity.Notification;
import com.freshtrace.notification.enums.NotificationType;
import com.freshtrace.notification.mapper.NotificationMapper;
import com.freshtrace.notification.service.NotificationService;
import com.freshtrace.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现（Phase 9）。
 * <p>
 * 幂等设计：写入以 dedup_key 唯一索引为最终兜底，MQ 重复投递触发唯一键冲突后静默忽略；
 * 批量写入逐条插入、互不回滚，保证「一个用户的重复/异常不影响其他用户」。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void push(Long userId, NotificationType type, String title, String content,
                     Long relatedId, String dedupKey) {
        if (userId == null || type == null) {
            log.warn("skip notification with null userId or type, userId={}, type={}", userId, type);
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setDedupKey(dedupKey);
        insertIgnoringDuplicate(notification);
    }

    @Override
    public int pushBatch(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (Notification notification : notifications) {
            if (notification.getIsRead() == null) {
                notification.setIsRead(0);
            }
            if (insertIgnoringDuplicate(notification)) {
                inserted++;
            }
        }
        return inserted;
    }

    /**
     * 插入通知；dedup_key 冲突说明该事件已消费过，静默忽略（幂等）。
     */
    private boolean insertIgnoringDuplicate(Notification notification) {
        try {
            notificationMapper.insert(notification);
            return true;
        } catch (DuplicateKeyException e) {
            log.info("duplicate notification ignored, userId={}, dedupKey={}",
                    notification.getUserId(), notification.getDedupKey());
            return false;
        }
    }

    @Override
    public PageVO<NotificationVO> page(Long userId, NotificationQueryDTO query) {
        Page<Notification> page = new Page<>(query.getPage(), query.getSize());
        notificationMapper.selectPage(page, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(query.getType() != null, Notification::getType, query.getType())
                .eq(query.getRead() != null, Notification::getIsRead, Boolean.TRUE.equals(query.getRead()) ? 1 : 0)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId));
        List<NotificationVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageVO.of(page, records);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BizException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        // 归属校验：只允许操作自己的通知，绝不信任客户端传入的 id
        if (!notification.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOTIFICATION_PERMISSION_DENIED);
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return;
        }
        Notification update = new Notification();
        update.setId(id);
        update.setIsRead(1);
        notificationMapper.updateById(update);
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        NotificationType type = NotificationType.fromCode(notification.getType());
        vo.setTypeDesc(type == null ? "" : type.getDesc());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setRelatedId(notification.getRelatedId());
        vo.setIsRead(notification.getIsRead());
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }
}
