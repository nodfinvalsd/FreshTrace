package com.freshtrace.farmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.utils.AesUtils;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.dto.FarmerAuditQueryDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.farmer.service.FarmerService;
import com.freshtrace.farmer.vo.AdminFarmerVO;
import com.freshtrace.farmer.vo.FarmerStatusVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmerServiceImpl implements FarmerService {

    private final FarmerMapper farmerMapper;
    private final UserMapper userMapper;
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

    @Override
    public PageVO<AdminFarmerVO> pageForAdmin(FarmerAuditQueryDTO query) {
        Page<Farmer> page = new Page<>(query.getPage(), query.getSize());
        // 待审核(0) 排前，其余按申请时间倒序，便于管理员优先处理
        farmerMapper.selectPage(page, new LambdaQueryWrapper<Farmer>()
                .eq(query.getAuditStatus() != null, Farmer::getAuditStatus, query.getAuditStatus())
                .orderByAsc(Farmer::getAuditStatus)
                .orderByDesc(Farmer::getCreateTime));
        List<Farmer> farmers = page.getRecords();
        if (farmers.isEmpty()) {
            return PageVO.empty(query.getPage(), query.getSize());
        }
        // 批量装配用户信息，避免 N+1
        Map<Long, User> userMap = batchLoadUsers(farmers.stream().map(Farmer::getUserId).distinct().toList());
        List<AdminFarmerVO> records = farmers.stream().map(farmer -> {
            AdminFarmerVO vo = toAdminFarmerVO(farmer, userMap.get(farmer.getUserId()));
            vo.setIdCard(null); // 列表不返回身份证号，详情接口才解密
            return vo;
        }).toList();
        return PageVO.of(page, records);
    }

    @Override
    public AdminFarmerVO adminDetail(Long id) {
        Farmer farmer = farmerMapper.selectById(id);
        if (farmer == null) {
            throw new BizException(ErrorCode.FARMER_NOT_EXISTS);
        }
        AdminFarmerVO vo = toAdminFarmerVO(farmer, userMapper.selectById(farmer.getUserId()));
        if (StringUtils.hasText(farmer.getIdCard())) {
            vo.setIdCard(aesUtils.decrypt(farmer.getIdCard()));
        }
        return vo;
    }

    private Map<Long, User> batchLoadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminFarmerVO toAdminFarmerVO(Farmer farmer, User user) {
        AdminFarmerVO vo = new AdminFarmerVO();
        vo.setId(farmer.getId());
        vo.setUserId(farmer.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setPhone(user == null ? null : user.getPhone());
        vo.setRealName(farmer.getRealName());
        vo.setIdCard(farmer.getIdCard());
        vo.setOrchardName(farmer.getOrchardName());
        vo.setOrchardProvince(farmer.getOrchardProvince());
        vo.setOrchardCity(farmer.getOrchardCity());
        vo.setOrchardDistrict(farmer.getOrchardDistrict());
        vo.setOrchardAddress(farmer.getOrchardAddress());
        vo.setOrchardArea(farmer.getOrchardArea());
        vo.setCategoryIds(parseJsonList(farmer.getCategoryIds()));
        vo.setOrchardPhotos(parseJsonList(farmer.getOrchardPhotos()));
        vo.setCertificationImage(farmer.getCertificationImage());
        vo.setAuditStatus(farmer.getAuditStatus());
        vo.setAuditReason(farmer.getAuditReason());
        vo.setAuditedAt(farmer.getAuditedAt());
        vo.setAuditedBy(farmer.getAuditedBy());
        vo.setCreateTime(farmer.getCreateTime());
        return vo;
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
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
