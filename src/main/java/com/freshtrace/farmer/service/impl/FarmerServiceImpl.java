package com.freshtrace.farmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.utils.AesUtils;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.farmer.service.FarmerService;
import com.freshtrace.farmer.vo.FarmerStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FarmerServiceImpl implements FarmerService {

    private final FarmerMapper farmerMapper;
    private final AesUtils aesUtils;
    private final ObjectMapper objectMapper;

    @Override
    public void apply(Long userId, FarmerApplyDTO dto) {
        Long count = farmerMapper.selectCount(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, userId));
        if (count > 0) {
            throw new BizException(ErrorCode.FARMER_ALREADY_EXISTS);
        }

        Farmer farmer = new Farmer();
        farmer.setUserId(userId);
        farmer.setRealName(dto.getRealName());
        farmer.setIdCard(aesUtils.encrypt(dto.getIdCard()));
        farmer.setOrchardName(dto.getOrchardName());
        farmer.setOrchardProvince(dto.getOrchardProvince());
        farmer.setOrchardCity(dto.getOrchardCity());
        farmer.setOrchardDistrict(dto.getOrchardDistrict());
        farmer.setOrchardAddress(dto.getOrchardAddress());
        farmer.setOrchardArea(dto.getOrchardArea());
        farmer.setCategoryIds(toJson(dto.getCategoryIds()));
        farmer.setOrchardPhotos(toJson(dto.getOrchardPhotos()));
        farmer.setCertificationImage(dto.getCertificationImage());
        farmer.setAuditStatus(0);
        farmerMapper.insert(farmer);
    }

    @Override
    public FarmerStatusVO getStatus(Long userId) {
        Farmer farmer = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, userId));
        if (farmer == null) {
            throw new BizException(ErrorCode.FARMER_NOT_EXISTS);
        }
        FarmerStatusVO vo = new FarmerStatusVO();
        vo.setAuditStatus(farmer.getAuditStatus());
        vo.setAuditReason(farmer.getAuditReason());
        return vo;
    }

    @Override
    public void audit(FarmerAuditDTO dto, Long adminId) {
        if (dto.getAuditStatus() != 1 && dto.getAuditStatus() != 2) {
            throw new BizException(ErrorCode.FARMER_AUDIT_STATUS_INVALID);
        }
        if (dto.getAuditStatus() == 2 && !StringUtils.hasText(dto.getAuditReason())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "驳回时必须填写审核意见");
        }
        Farmer farmer = farmerMapper.selectById(dto.getFarmerId());
        if (farmer == null) {
            throw new BizException(ErrorCode.FARMER_NOT_EXISTS);
        }
        if (farmer.getAuditStatus() == null || farmer.getAuditStatus() != 0) {
            throw new BizException(ErrorCode.FARMER_AUDIT_NOT_PENDING);
        }
        farmer.setAuditStatus(dto.getAuditStatus());
        farmer.setAuditReason(dto.getAuditStatus() == 1 ? null : dto.getAuditReason());
        farmer.setAuditedAt(LocalDateTime.now());
        farmer.setAuditedBy(adminId);
        farmerMapper.updateById(farmer);
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
}
