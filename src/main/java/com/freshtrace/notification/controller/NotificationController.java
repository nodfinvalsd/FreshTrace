package com.freshtrace.notification.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.notification.dto.NotificationQueryDTO;
import com.freshtrace.notification.service.NotificationService;
import com.freshtrace.notification.vo.NotificationVO;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（Phase 9）。
 * <p>
 * 登录即可访问；userId 一律取自 UserContext，归属校验在服务层完成。
 * Controller 不含业务逻辑。
 */
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 通知列表（分页，可按类型/已读状态过滤），创建时间倒序。
     */
    @GetMapping("/list")
    public R<PageVO<NotificationVO>> list(@Valid @ModelAttribute NotificationQueryDTO query) {
        return R.ok(notificationService.page(UserContext.get().getUserId(), query));
    }

    /**
     * 未读通知数，用于全局角标。
     */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount(UserContext.get().getUserId()));
    }

    /**
     * 标记单条已读。
     */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContext.get().getUserId(), id);
        return R.ok();
    }

    /**
     * 全部标记已读。
     */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead(UserContext.get().getUserId());
        return R.ok();
    }
}
