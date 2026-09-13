package com.freshtrace.admin.controller;

import com.freshtrace.admin.support.OperationLog;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.security.RoleRequired;
import com.freshtrace.security.UserContext;
import com.freshtrace.trade.dto.RefundArbitrateDTO;
import com.freshtrace.trade.dto.RefundQueryDTO;
import com.freshtrace.trade.service.RefundService;
import com.freshtrace.trade.vo.RefundResultVO;
import com.freshtrace.trade.vo.RefundVO;
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
 * 管理端退款仲裁接口（/admin/**，@RoleRequired(role=1) 保护）。
 */
@RestController
@RequestMapping("/admin/refund")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;

    /** 退款工单列表：分页 + 可按状态筛选（0=待处理,2=果农拒绝 为可仲裁状态） */
    @GetMapping("/list")
    @RoleRequired(role = 1)
    public R<PageVO<RefundVO>> list(@Valid @ModelAttribute RefundQueryDTO query) {
        return R.ok(refundService.pageForAdmin(query));
    }

    /** 工单详情（含凭证图片） */
    @GetMapping("/{id}")
    @RoleRequired(role = 1)
    public R<RefundVO> detail(@PathVariable Long id) {
        return R.ok(refundService.adminDetail(id));
    }

    /** 仲裁：同意退款或驳回。targetId 取路径变量 id */
    @PostMapping("/{id}/arbitrate")
    @RoleRequired(role = 1)
    @OperationLog(action = "退款仲裁", targetType = "REFUND")
    public R<RefundResultVO> arbitrate(@PathVariable Long id, @Valid @RequestBody RefundArbitrateDTO dto) {
        return R.ok(refundService.arbitrate(UserContext.get().getUserId(), id, dto));
    }
}
