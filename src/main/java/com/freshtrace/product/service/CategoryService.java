package com.freshtrace.product.service;

import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.CategoryUpdateDTO;
import com.freshtrace.product.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    CategoryVO create(CategoryCreateDTO dto);

    CategoryVO getById(Long id);

    List<CategoryVO> list();

    CategoryVO update(Long id, CategoryUpdateDTO dto);

    void delete(Long id);
}
