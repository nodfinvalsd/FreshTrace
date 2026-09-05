package com.freshtrace.trade.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.trade.dto.PayOrderDTO;
import com.freshtrace.trade.service.PaymentService;
import com.freshtrace.trade.vo.PaymentResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/pay")
    public R<PaymentResultVO> pay(@Valid @RequestBody PayOrderDTO dto,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(paymentService.pay(UserContext.get().getUserId(), dto, idempotencyKey));
    }
}
