package com.freshtrace.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
