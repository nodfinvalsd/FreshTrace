package com.freshtrace.community.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.community.dto.CommentCreateDTO;
import com.freshtrace.community.dto.PostCreateDTO;
import com.freshtrace.community.dto.PostQueryDTO;
import com.freshtrace.community.service.PostCommentService;
import com.freshtrace.community.service.PostLikeService;
import com.freshtrace.community.service.PostService;
import com.freshtrace.community.vo.CommentVO;
import com.freshtrace.community.vo.PostDetailVO;
import com.freshtrace.community.vo.PostVO;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.security.LoginUser;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态接口（Phase 6 Day 2/Day 3）。
 * <p>
 * - 发布/删除：@FarmerRequired 切面校验后经 FarmerContext 注入 farmerId；
 * - 点赞/评论：登录用户即可（SecurityConfig 全局 authenticated），userId 取自 UserContext；
 * - 列表/详情：补全 liked 状态与评论树（匿名时 liked 恒 false）。
 * Controller 不包含任何业务逻辑。
 */
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;
    private final PostCommentService postCommentService;

    @PostMapping
    @FarmerRequired
    public R<PostVO> create(@Valid @RequestBody PostCreateDTO dto) {
        return R.ok(postService.createPost(FarmerContext.get(), dto));
    }

    @GetMapping("/page")
    public R<PageVO<PostVO>> page(@Valid @ModelAttribute PostQueryDTO query) {
        return R.ok(postService.page(query, currentUserId()));
    }

    @GetMapping("/{id}")
    public R<PostDetailVO> detail(@PathVariable Long id) {
        return R.ok(postService.detail(id, currentUserId()));
    }

    @DeleteMapping("/{id}")
    @FarmerRequired
    public R<Void> delete(@PathVariable Long id) {
        postService.deletePost(FarmerContext.get(), id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Void> like(@PathVariable Long id) {
        postLikeService.like(UserContext.get().getUserId(), id);
        return R.ok();
    }

    @DeleteMapping("/{id}/like")
    public R<Void> unlike(@PathVariable Long id) {
        postLikeService.unlike(UserContext.get().getUserId(), id);
        return R.ok();
    }

    @PostMapping("/{id}/comment")
    public R<CommentVO> comment(@PathVariable Long id, @Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(postCommentService.createComment(UserContext.get().getUserId(), id, dto));
    }

    private Long currentUserId() {
        LoginUser loginUser = UserContext.get();
        return loginUser == null ? null : loginUser.getUserId();
    }
}
