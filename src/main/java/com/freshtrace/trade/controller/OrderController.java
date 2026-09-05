package com.freshtrace.trade.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.trade.dto.CreateOrderDTO;
import com.freshtrace.trade.dto.OrderQueryDTO;
import com.freshtrace.trade.service.OrderCancelService;
import com.freshtrace.trade.service.OrderQueryService;
import com.freshtrace.trade.service.OrderService;
import com.freshtrace.trade.vo.CancelOrderVO;
import com.freshtrace.trade.vo.OrderCreateVO;
import com.freshtrace.trade.vo.OrderDetailVO;
import com.freshtrace.trade.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderCancelService orderCancelService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public R<OrderCreateVO> create(@Valid @RequestBody CreateOrderDTO dto,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(orderService.createOrder(UserContext.get().getUserId(), dto, idempotencyKey));
    }

    @PostMapping("/{orderNo}/cancel")
    public R<CancelOrderVO> cancel(@PathVariable String orderNo) {
        return R.ok(orderCancelService.cancelByUser(UserContext.get().getUserId(), orderNo));
    }

    @GetMapping
    public R<PageVO<OrderVO>> page(@Valid @ModelAttribute OrderQueryDTO query) {
        return R.ok(orderQueryService.page(UserContext.get().getUserId(), query));
    }

    @GetMapping("/{orderNo}")
    public R<OrderDetailVO> detail(@PathVariable String orderNo) {
        return R.ok(orderQueryService.detail(UserContext.get().getUserId(), orderNo));
    }
}
