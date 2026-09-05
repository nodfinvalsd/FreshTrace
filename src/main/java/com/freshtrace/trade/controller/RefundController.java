package com.freshtrace.trade.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.trade.dto.RefundApplyDTO;
import com.freshtrace.trade.service.RefundService;
import com.freshtrace.trade.vo.RefundResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/{subOrderNo}")
    public R<RefundResultVO> refund(@PathVariable String subOrderNo, @Valid @RequestBody RefundApplyDTO dto) {
        return R.ok(refundService.refund(UserContext.get().getUserId(), subOrderNo, dto));
    }
}
