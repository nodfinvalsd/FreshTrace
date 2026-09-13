package com.freshtrace.admin.controller;

import com.freshtrace.admin.support.OperationLog;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.report.dto.ReportHandleDTO;
import com.freshtrace.report.dto.ReportQueryDTO;
import com.freshtrace.report.service.ReportService;
import com.freshtrace.report.vo.ReportVO;
import com.freshtrace.security.RoleRequired;
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
 * 管理端举报处理接口（/admin/**，@RoleRequired(role=1) 保护）。
 */
@RestController
@RequestMapping("/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    /** 举报列表：分页 + 可按状态/对象类型筛选 */
    @GetMapping("/list")
    @RoleRequired(role = 1)
    public R<PageVO<ReportVO>> list(@Valid @ModelAttribute ReportQueryDTO query) {
        return R.ok(reportService.pageForAdmin(query));
    }

    /** 举报详情（含凭证图片） */
    @GetMapping("/{id}")
    @RoleRequired(role = 1)
    public R<ReportVO> detail(@PathVariable Long id) {
        return R.ok(reportService.adminDetail(id));
    }

    /** 处理举报：1=有效,2=驳回。targetId 取路径变量 id */
    @PostMapping("/{id}/handle")
    @RoleRequired(role = 1)
    @OperationLog(action = "处理举报", targetType = "REPORT")
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody ReportHandleDTO dto) {
        reportService.handle(UserContext.get().getUserId(), id, dto);
        return R.ok();
    }
}
