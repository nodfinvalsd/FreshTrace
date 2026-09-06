package com.freshtrace.review.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.review.dto.ReviewCreateDTO;
import com.freshtrace.review.dto.ReviewQueryDTO;
import com.freshtrace.review.dto.ReviewReplyDTO;
import com.freshtrace.review.service.ReviewService;
import com.freshtrace.review.vo.ReviewVO;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价接口（Phase 4 Day 3）。
 * <p>
 * - 发表评价：买家身份来自 UserContext；
 * - 果农回复：@FarmerRequired 切面校验后经 FarmerContext 注入 farmerId；
 * - 列表查询：productId / farmerId 至少其一。
 * Controller 不包含任何业务逻辑。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public R<ReviewVO> create(@Valid @RequestBody ReviewCreateDTO dto) {
        return R.ok(reviewService.createReview(UserContext.get().getUserId(), dto));
    }

    @PostMapping("/{id}/reply")
    @FarmerRequired
    public R<Void> reply(@PathVariable Long id, @Valid @RequestBody ReviewReplyDTO dto) {
        reviewService.reply(FarmerContext.get(), id, dto);
        return R.ok();
    }

    @GetMapping
    public R<PageVO<ReviewVO>> page(@Valid @ModelAttribute ReviewQueryDTO query) {
        return R.ok(reviewService.page(query));
    }
}
