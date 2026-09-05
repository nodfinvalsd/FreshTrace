package com.freshtrace.product.service;

import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.dto.ProductUpdateDTO;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.product.vo.ProductVO;

public interface ProductService {

    ProductVO create(Long userId, ProductCreateDTO dto);

    ProductDetailVO detail(Long id);

    ProductVO update(Long userId, Long id, ProductUpdateDTO dto);

    void audit(Long id, ProductAuditDTO dto);

    ProductVO updateLifecycle(Long userId, Long id, ProductLifecycleUpdateDTO dto);

    ProductVO cancelPreSale(Long userId, Long id);

    ProductVO restock(Long userId, Long id);
}
