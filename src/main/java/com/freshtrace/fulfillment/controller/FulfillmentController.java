package com.freshtrace.fulfillment.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.fulfillment.dto.FarmerSubOrderQueryDTO;
import com.freshtrace.fulfillment.dto.ShipDTO;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.fulfillment.vo.FarmerSubOrderVO;
import com.freshtrace.fulfillment.vo.ShipResultVO;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
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
 * 履约接口（Phase 4 Day 1：发货）。
 * <p>
 * 全部接口要求认证果农身份：{@code @FarmerRequired} 切面校验后
 * 将 farmerId 写入 {@link FarmerContext}，Service 层直接取用。
 */
@RestController
@RequestMapping("/farmer")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/sub-orders")
    @FarmerRequired
    public R<PageVO<FarmerSubOrderVO>> subOrders(@Valid @ModelAttribute FarmerSubOrderQueryDTO query) {
        return R.ok(fulfillmentService.pageFarmerSubOrders(FarmerContext.get(), query));
    }

    @PostMapping("/sub-order/{subOrderNo}/ship")
    @FarmerRequired
    public R<ShipResultVO> ship(@PathVariable String subOrderNo, @Valid @RequestBody ShipDTO dto) {
        return R.ok(fulfillmentService.ship(FarmerContext.get(), subOrderNo, dto));
    }
}
