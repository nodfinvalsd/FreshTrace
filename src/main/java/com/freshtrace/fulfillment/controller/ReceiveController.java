package com.freshtrace.fulfillment.controller;

import com.freshtrace.common.R;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.fulfillment.vo.ReceiveResultVO;
import com.freshtrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家确认收货接口（Phase 4 Day 2）。
 * <p>
 * 买家身份来自 UserContext（JWT），Service 层完成「子订单 → 主订单 → userId」归属校验，
 * Controller 不写任何业务逻辑。
 */
@RestController
@RequestMapping("/sub-order")
@RequiredArgsConstructor
public class ReceiveController {

    private final FulfillmentService fulfillmentService;

    @PostMapping("/{subOrderNo}/receive")
    public R<ReceiveResultVO> receive(@PathVariable String subOrderNo) {
        return R.ok(fulfillmentService.receive(UserContext.get().getUserId(), subOrderNo));
    }
}
