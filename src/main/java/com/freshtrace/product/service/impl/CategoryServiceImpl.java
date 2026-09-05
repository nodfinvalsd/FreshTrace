package com.freshtrace.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.CategoryUpdateDTO;
import com.freshtrace.product.entity.Category;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.product.service.CategoryService;
import com.freshtrace.product.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final SpuMapper spuMapper;

    @Override
    public CategoryVO create(CategoryCreateDTO dto) {
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, dto.getName())) > 0) {
            throw new BizException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        Category category = new Category();
        category.setName(dto.getName());
        category.setIconUrl(dto.getIconUrl());
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        categoryMapper.insert(category);
        return toVO(category);
    }

    @Override
    public CategoryVO getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return toVO(category);
    }

    @Override
    public List<CategoryVO> list() {
        List<Category> list = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public CategoryVO update(Long id, CategoryUpdateDTO dto) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, dto.getName())
                .ne(Category::getId, id)) > 0) {
            throw new BizException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        category.setName(dto.getName());
        category.setIconUrl(dto.getIconUrl());
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        categoryMapper.updateById(category);
        return toVO(category);
    }

    @Override
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (spuMapper.selectCount(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getCategoryId, id)) > 0) {
            throw new BizException(ErrorCode.CATEGORY_DELETE_FAILED);
        }
        categoryMapper.deleteById(id);
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIconUrl(category.getIconUrl());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }
}
