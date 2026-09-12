package com.freshtrace.im.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.dto.ConversationQueryDTO;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.vo.ChatHistoryVO;
import com.freshtrace.im.vo.ConversationVO;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 即时通讯接口（Phase 8）：会话创建/列表、历史消息。
 * <p>
 * 登录即可访问；userId 由 UserContext 获取，果农/买家视角与归属校验由服务端判定。
 */
@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class ImController {

    private final ConversationService conversationService;

    private final ChatMessageService chatMessageService;

    /**
     * 创建或复用与果农的会话（买家从商品详情页发起）。
     */
    @PostMapping("/conversations")
    public R<ConversationVO> create(@Valid @RequestBody ConversationCreateDTO dto) {
        return R.ok(conversationService.create(UserContext.get().getUserId(), dto));
    }

    /**
     * 会话列表：按登录用户视角返回，最后消息时间倒序。
     */
    @GetMapping("/conversations")
    public R<PageVO<ConversationVO>> list(@Valid @ModelAttribute ConversationQueryDTO query) {
        return R.ok(conversationService.list(UserContext.get().getUserId(), query));
    }

    /**
     * 历史消息：游标分页（lastId 为空取最新一页），仅会话参与方可读。
     */
    @GetMapping("/conversations/{id}/messages")
    public R<ChatHistoryVO> history(@PathVariable Long id,
                                    @RequestParam(required = false) Long lastId,
                                    @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(chatMessageService.history(UserContext.get().getUserId(), id, lastId, size));
    }

    /**
     * 标记会话已读：清零本方未读并把对方消息置为已读，仅会话参与方可用。
     */
    @PutMapping("/conversations/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        conversationService.markRead(UserContext.get().getUserId(), id);
        return R.ok();
    }

    /**
     * 当前用户总未读数，用于全局消息角标。
     */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(conversationService.unreadTotal(UserContext.get().getUserId()));
    }
}
