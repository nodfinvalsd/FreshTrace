package com.freshtrace.farmer.controller;

import com.freshtrace.admin.support.OperationLog;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.dto.FarmerAuditQueryDTO;
import com.freshtrace.farmer.service.FarmerService;
import com.freshtrace.farmer.vo.AdminFarmerVO;
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
 * 管理端果农认证审核接口（/admin/** 命名空间，@RoleRequired(role=1) 保护）。
 */
@RestController
@RequestMapping("/admin/farmer")
@RequiredArgsConstructor
public class AdminFarmerController {

    private final FarmerService farmerService;

    /**
     * 认证审核列表：分页 + 可按认证状态筛选（0=待审核,1=通过,2=驳回）。
     */
    @GetMapping("/list")
    @RoleRequired(role = 1)
    public R<PageVO<AdminFarmerVO>> list(@Valid @ModelAttribute FarmerAuditQueryDTO query) {
        return R.ok(farmerService.pageForAdmin(query));
    }

    /**
     * 认证详情：解密身份证号，供管理员审核核验。
     */
    @GetMapping("/{id}")
    @RoleRequired(role = 1)
    public R<AdminFarmerVO> detail(@PathVariable Long id) {
        return R.ok(farmerService.adminDetail(id));
    }

    /**
     * 审核通过/驳回。targetId 取自 DTO 的 farmerId。
     */
    @PostMapping("/audit")
    @RoleRequired(role = 1)
    @OperationLog(action = "审核果农认证", targetType = "FARMER", targetIdProperty = "farmerId")
    public R<Void> audit(@Valid @RequestBody FarmerAuditDTO dto) {
        farmerService.audit(dto, UserContext.get().getUserId());
        return R.ok();
    }
}
