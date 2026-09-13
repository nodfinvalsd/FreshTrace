package com.freshtrace.admin.controller;

import com.freshtrace.admin.service.AdminDashboardService;
import com.freshtrace.admin.vo.DashboardVO;
import com.freshtrace.common.R;
import com.freshtrace.security.RoleRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端运营仪表板接口（/admin/**，@RoleRequired(role=1) 保护）。
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /** 运营总览：当日/本周/本月指标 + 待审核数 */
    @GetMapping("/overview")
    @RoleRequired(role = 1)
    public R<DashboardVO> overview() {
        return R.ok(adminDashboardService.getOverview());
    }
}
