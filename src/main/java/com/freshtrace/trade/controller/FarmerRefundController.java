package com.freshtrace.trade.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.trade.dto.RefundHandleDTO;
import com.freshtrace.trade.service.RefundService;
import com.freshtrace.trade.vo.RefundResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 果农售后处理接口（/farmer/**，@FarmerRequired 校验果农身份并写入 FarmerContext）。
 */
@RestController
@RequestMapping("/farmer/refund")
@RequiredArgsConstructor
public class FarmerRefundController {

    private final RefundService refundService;

    /** 同意退款 */
    @PostMapping("/{id}/approve")
    @FarmerRequired
    public R<RefundResultVO> approve(@PathVariable Long id, @Valid @RequestBody RefundHandleDTO dto) {
        return R.ok(refundService.farmerApprove(FarmerContext.get(), id, dto));
    }

    /** 拒绝退款（进入平台仲裁池） */
    @PostMapping("/{id}/reject")
    @FarmerRequired
    public R<RefundResultVO> reject(@PathVariable Long id, @Valid @RequestBody RefundHandleDTO dto) {
        return R.ok(refundService.farmerReject(FarmerContext.get(), id, dto));
    }
}
