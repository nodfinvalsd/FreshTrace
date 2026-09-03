package com.freshtrace.farmer.controller;

import com.freshtrace.common.R;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.service.FarmerService;
import com.freshtrace.farmer.vo.FarmerStatusVO;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farmer")
@RequiredArgsConstructor
public class FarmerController {

    private final FarmerService farmerService;

    @PostMapping("/apply")
    public R<Void> apply(@Valid @RequestBody FarmerApplyDTO dto) {
        farmerService.apply(UserContext.get().getUserId(), dto);
        return R.ok();
    }

    @GetMapping("/status")
    public R<FarmerStatusVO> status() {
        return R.ok(farmerService.getStatus(UserContext.get().getUserId()));
    }

    @GetMapping("/privilege")
    @FarmerRequired
    public R<String> privilege() {
        return R.ok("果农权限验证通过");
    }
}
