package com.freshtrace.product.controller;

import com.freshtrace.common.R;
import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.product.dto.SpuUpdateDTO;
import com.freshtrace.product.service.SpuService;
import com.freshtrace.product.vo.SpuVO;
import com.freshtrace.security.RoleRequired;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    @PostMapping
    @RoleRequired(role = 1)
    public R<SpuVO> create(@Valid @RequestBody SpuCreateDTO dto) {
        return R.ok(spuService.create(dto));
    }

    @GetMapping("/list")
    public R<List<SpuVO>> list() {
        return R.ok(spuService.list());
    }

    @GetMapping("/{id}")
    public R<SpuVO> detail(@PathVariable Long id) {
        return R.ok(spuService.getById(id));
    }

    @PutMapping("/{id}")
    @RoleRequired(role = 1)
    public R<SpuVO> update(@PathVariable Long id, @Valid @RequestBody SpuUpdateDTO dto) {
        return R.ok(spuService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RoleRequired(role = 1)
    public R<Void> delete(@PathVariable Long id) {
        spuService.delete(id);
        return R.ok();
    }
}
