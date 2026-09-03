package com.freshtrace.farmer.controller;

import com.freshtrace.common.R;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.service.FarmerService;
import com.freshtrace.security.RoleRequired;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/farmer")
@RequiredArgsConstructor
public class AdminFarmerController {

    private final FarmerService farmerService;

    @PostMapping("/audit")
    @RoleRequired(role = 1)
    public R<Void> audit(@Valid @RequestBody FarmerAuditDTO dto) {
        farmerService.audit(dto, UserContext.get().getUserId());
        return R.ok();
    }
}
