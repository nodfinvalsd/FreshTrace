package com.freshtrace.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.product.dto.SpuAttributeDTO;
import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.product.dto.SpuUpdateDTO;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.entity.SpuAttribute;
import com.freshtrace.product.mapper.CategoryMapper;
import com.freshtrace.product.mapper.SpuAttributeMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.product.service.SpuService;
import com.freshtrace.product.vo.SpuAttributeVO;
import com.freshtrace.product.vo.SpuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SpuAttributeMapper spuAttributeMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SpuVO create(SpuCreateDTO dto) {
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BizException(ErrorCode.SPU_CATEGORY_NOT_FOUND);
        }
        Spu spu = new Spu();
        copy(dto, spu);
        spuMapper.insert(spu);
        saveAttributes(spu.getId(), dto.getAttributes());
        return getById(spu.getId());
    }

    @Override
    public SpuVO getById(Long id) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND);
        }
        List<SpuAttribute> attributes = spuAttributeMapper.selectList(new LambdaQueryWrapper<SpuAttribute>()
                .eq(SpuAttribute::getSpuId, id)
                .orderByAsc(SpuAttribute::getSortOrder)
                .orderByAsc(SpuAttribute::getId));
        return toVO(spu, attributes);
    }

    @Override
    public List<SpuVO> list() {
        List<Spu> list = spuMapper.selectList(new LambdaQueryWrapper<Spu>()
                .orderByDesc(Spu::getCreateTime));
        return list.stream().map(spu -> toVO(spu, null)).toList();
    }

    @Override
    @Transactional
    public SpuVO update(Long id, SpuUpdateDTO dto) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND);
        }
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BizException(ErrorCode.SPU_CATEGORY_NOT_FOUND);
        }
        copy(dto, spu);
        spuMapper.updateById(spu);
        if (dto.getAttributes() != null) {
            saveAttributes(id, dto.getAttributes());
        }
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ErrorCode.SPU_NOT_FOUND);
        }
        spuMapper.deleteById(id);
        spuAttributeMapper.delete(new LambdaQueryWrapper<SpuAttribute>()
                .eq(SpuAttribute::getSpuId, id));
    }

    private void saveAttributes(Long spuId, List<SpuAttributeDTO> attributes) {
        spuAttributeMapper.delete(new LambdaQueryWrapper<SpuAttribute>()
                .eq(SpuAttribute::getSpuId, spuId));
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (SpuAttributeDTO dto : attributes) {
            SpuAttribute attribute = new SpuAttribute();
            attribute.setSpuId(spuId);
            attribute.setAttrName(dto.getAttrName());
            attribute.setAttrValues(toJson(dto.getAttrValues()));
            attribute.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
            spuAttributeMapper.insert(attribute);
        }
    }

    private void copy(SpuCreateDTO dto, Spu spu) {
        spu.setCategoryId(dto.getCategoryId());
        spu.setName(dto.getName());
        spu.setVariety(dto.getVariety());
        spu.setOrigin(dto.getOrigin());
        spu.setDescription(dto.getDescription());
        spu.setMainImage(dto.getMainImage());
        spu.setTags(toJson(dto.getTags()));
        spu.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
    }

    private SpuVO toVO(Spu spu, List<SpuAttribute> attributes) {
        SpuVO vo = new SpuVO();
        vo.setId(spu.getId());
        vo.setCategoryId(spu.getCategoryId());
        vo.setName(spu.getName());
        vo.setVariety(spu.getVariety());
        vo.setOrigin(spu.getOrigin());
        vo.setDescription(spu.getDescription());
        vo.setMainImage(spu.getMainImage());
        vo.setTags(parseStringList(spu.getTags()));
        vo.setStatus(spu.getStatus());
        if (attributes != null) {
            vo.setAttributes(attributes.stream().map(this::toAttributeVO).toList());
        }
        return vo;
    }

    private SpuAttributeVO toAttributeVO(SpuAttribute attribute) {
        SpuAttributeVO vo = new SpuAttributeVO();
        vo.setId(attribute.getId());
        vo.setAttrName(attribute.getAttrName());
        vo.setAttrValues(parseStringList(attribute.getAttrValues()));
        vo.setSortOrder(attribute.getSortOrder());
        return vo;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException("序列化失败");
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BizException("JSON 解析失败");
        }
    }
}
