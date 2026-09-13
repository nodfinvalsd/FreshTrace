package com.freshtrace.report.controller;

import com.freshtrace.common.R;
import com.freshtrace.report.dto.ReportSubmitDTO;
import com.freshtrace.report.service.ReportService;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户举报提交接口。
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 提交举报（举报人取自登录态，不信任客户端） */
    @PostMapping
    public R<Void> submit(@Valid @RequestBody ReportSubmitDTO dto) {
        reportService.submit(UserContext.get().getUserId(), dto);
        return R.ok();
    }
}
