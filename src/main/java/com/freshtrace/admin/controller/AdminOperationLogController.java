package com.freshtrace.admin.controller;

import com.freshtrace.admin.dto.OperationLogQueryDTO;
import com.freshtrace.admin.service.OperationLogService;
import com.freshtrace.admin.vo.OperationLogVO;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.security.RoleRequired;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作日志查询接口（/admin/**，@RoleRequired(role=1) 保护）。
 */
@RestController
@RequestMapping("/admin/operation-log")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    /** 操作日志列表：分页 + 按操作人/对象类型/动作/时间段筛选 */
    @GetMapping("/list")
    @RoleRequired(role = 1)
    public R<PageVO<OperationLogVO>> list(@Valid @ModelAttribute OperationLogQueryDTO query) {
        return R.ok(operationLogService.page(query));
    }
}
