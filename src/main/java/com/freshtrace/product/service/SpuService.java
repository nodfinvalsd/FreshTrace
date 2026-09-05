package com.freshtrace.product.service;

import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.product.dto.SpuUpdateDTO;
import com.freshtrace.product.vo.SpuVO;

import java.util.List;

public interface SpuService {

    SpuVO create(SpuCreateDTO dto);

    SpuVO getById(Long id);

    List<SpuVO> list();

    SpuVO update(Long id, SpuUpdateDTO dto);

    void delete(Long id);
}
