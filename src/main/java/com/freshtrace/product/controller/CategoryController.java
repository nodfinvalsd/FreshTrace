package com.freshtrace.product.controller;

import com.freshtrace.common.R;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.CategoryUpdateDTO;
import com.freshtrace.product.service.CategoryService;
import com.freshtrace.product.vo.CategoryVO;
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
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @RoleRequired(role = 1)
    public R<CategoryVO> create(@Valid @RequestBody CategoryCreateDTO dto) {
        return R.ok(categoryService.create(dto));
    }

    @GetMapping("/list")
    public R<List<CategoryVO>> list() {
        return R.ok(categoryService.list());
    }

    @GetMapping("/{id}")
    public R<CategoryVO> detail(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PutMapping("/{id}")
    @RoleRequired(role = 1)
    public R<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateDTO dto) {
        return R.ok(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RoleRequired(role = 1)
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
