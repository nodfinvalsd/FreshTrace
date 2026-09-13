package com.freshtrace.admin.controller;

import com.freshtrace.admin.support.OperationLog;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductAuditQueryDTO;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.AdminProductVO;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.security.RoleRequired;
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
 * 管理端商品审核接口。
 * <p>
 * 原 {@code POST /product/{id}/audit} 已迁入 {@code /admin/**} 命名空间，由 {@code @RoleRequired(role=1)} 保护。
 */
@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /**
     * 商品审核列表：分页 + 可按审核状态筛选（0=待审核,1=通过,2=驳回）。
     */
    @GetMapping("/audit-list")
    @RoleRequired(role = 1)
    public R<PageVO<AdminProductVO>> auditList(@Valid @ModelAttribute ProductAuditQueryDTO query) {
        return R.ok(productService.pageForAdmin(query));
    }

    /**
     * 商品详情（含 SPU / 品类 / 果农 / 属性 / 图片），供管理员审核查看。
     */
    @GetMapping("/{id}")
    @RoleRequired(role = 1)
    public R<ProductDetailVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    /**
     * 审核通过/驳回。targetId 取路径变量 id（方法第 0 个参数）。
     */
    @PostMapping("/{id}/audit")
    @RoleRequired(role = 1)
    @OperationLog(action = "审核商品", targetType = "PRODUCT")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody ProductAuditDTO dto) {
        productService.audit(id, dto);
        return R.ok();
    }
}
