package com.freshtrace.product.controller;

import com.freshtrace.common.PageVO;
import com.freshtrace.common.R;
import com.freshtrace.product.search.ProductSearchItemVO;
import com.freshtrace.product.search.ProductSearchQueryDTO;
import com.freshtrace.product.search.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品搜索接口（ES）。ES 关闭时该 Controller 不注册。
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/search")
    public R<PageVO<ProductSearchItemVO>> search(ProductSearchQueryDTO query) {
        return R.ok(productSearchService.search(query));
    }
}
