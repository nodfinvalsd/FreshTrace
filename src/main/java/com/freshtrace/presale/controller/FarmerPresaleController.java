package com.freshtrace.presale.controller;

import com.freshtrace.common.R;
import com.freshtrace.presale.dto.PresaleCreateDTO;
import com.freshtrace.presale.dto.PresaleUpdateDTO;
import com.freshtrace.presale.service.PresaleService;
import com.freshtrace.presale.vo.PresaleVO;
import com.freshtrace.security.FarmerContext;
import com.freshtrace.security.FarmerRequired;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 果农预售配置接口（Phase 7 Day 1）。
 * <p>
 * 果农身份由 {@code @FarmerRequired} 切面校验后经 {@link FarmerContext} 注入，
 * Controller 不包含业务逻辑。
 */
@RestController
@RequestMapping("/farmer/presale")
@RequiredArgsConstructor
public class FarmerPresaleController {

    private final PresaleService presaleService;

    @PostMapping
    @FarmerRequired
    public R<PresaleVO> create(@Valid @RequestBody PresaleCreateDTO dto) {
        return R.ok(presaleService.create(FarmerContext.get(), dto));
    }

    @PutMapping("/{id}")
    @FarmerRequired
    public R<PresaleVO> update(@PathVariable Long id, @Valid @RequestBody PresaleUpdateDTO dto) {
        return R.ok(presaleService.update(FarmerContext.get(), id, dto));
    }

    @PostMapping("/{id}/close")
    @FarmerRequired
    public R<Void> close(@PathVariable Long id) {
        presaleService.close(FarmerContext.get(), id);
        return R.ok();
    }
}
