package com.freshtrace.presale.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.presale.dto.PresaleQueryDTO;
import com.freshtrace.presale.dto.ReserveDTO;
import com.freshtrace.presale.service.PresaleReservationService;
import com.freshtrace.presale.service.PresaleService;
import com.freshtrace.presale.vo.PresaleVO;
import com.freshtrace.presale.vo.ReservationVO;
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
 * 预售接口（Phase 7 Day 2）。
 * <p>
 * - 列表/详情：无需果农/管理员身份，与 trace/product 等现有接口一致，登录即可访问；<br>
 * - 预约 / 我的预约：需登录，userId 由 UserContext 获取，绝不信任客户端。
 */
@RestController
@RequestMapping("/presale")
@RequiredArgsConstructor
public class PresaleController {

    private final PresaleService presaleService;
    private final PresaleReservationService presaleReservationService;

    @GetMapping("/list")
    public R<PageVO<PresaleVO>> list(@Valid @ModelAttribute PresaleQueryDTO query) {
        return R.ok(presaleService.pageOnGoing(query));
    }

    @GetMapping("/{id}")
    public R<PresaleVO> detail(@PathVariable Long id) {
        return R.ok(presaleService.detail(id));
    }

    @PostMapping("/{id}/reserve")
    public R<Void> reserve(@PathVariable Long id, @Valid @RequestBody ReserveDTO dto) {
        presaleReservationService.reserve(UserContext.get().getUserId(), id, dto);
        return R.ok();
    }

    @GetMapping("/my-reservations")
    public R<PageVO<ReservationVO>> myReservations(@Valid @ModelAttribute PresaleQueryDTO query) {
        return R.ok(presaleReservationService.myReservations(UserContext.get().getUserId(), query));
    }
}
