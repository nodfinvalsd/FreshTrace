package com.freshtrace.product.controller;

import com.freshtrace.common.R;
import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.dto.ProductUpdateDTO;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.product.vo.ProductVO;
import com.freshtrace.security.FarmerRequired;
import com.freshtrace.security.RoleRequired;
import com.freshtrace.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @FarmerRequired
    public R<ProductVO> create(@Valid @RequestBody ProductCreateDTO dto) {
        return R.ok(productService.create(UserContext.get().getUserId(), dto));
    }

    @GetMapping("/{id}")
    public R<ProductDetailVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @PutMapping("/{id}")
    @FarmerRequired
    public R<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateDTO dto) {
        return R.ok(productService.update(UserContext.get().getUserId(), id, dto));
    }

    @PostMapping("/{id}/audit")
    @RoleRequired(role = 1)
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody ProductAuditDTO dto) {
        productService.audit(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/lifecycle")
    @FarmerRequired
    public R<ProductVO> updateLifecycle(@PathVariable Long id, @Valid @RequestBody ProductLifecycleUpdateDTO dto) {
        return R.ok(productService.updateLifecycle(UserContext.get().getUserId(), id, dto));
    }

    @PostMapping("/{id}/cancel-pre-sale")
    @FarmerRequired
    public R<ProductVO> cancelPreSale(@PathVariable Long id) {
        return R.ok(productService.cancelPreSale(UserContext.get().getUserId(), id));
    }

    @PostMapping("/{id}/restock")
    @FarmerRequired
    public R<ProductVO> restock(@PathVariable Long id) {
        return R.ok(productService.restock(UserContext.get().getUserId(), id));
    }
}
