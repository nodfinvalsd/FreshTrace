package com.freshtrace.im.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IM 身份解析支撑。
 * <p>
 * 买家和果农互斥（Phase 1 设计）：登录用户若有 audit_status=1 的 t_farmer 记录即为果农视角，
 * 否则为买家视角。会话列表、消息路由均据此判定，不信任客户端传入的角色。
 */
@Component
@RequiredArgsConstructor
public class ImIdentitySupport {

    private final FarmerMapper farmerMapper;

    public Farmer findByUserId(Long userId) {
        return farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, userId));
    }

    /**
     * 当前登录用户若为已认证果农，返回其 farmer.id；否则返回 null（买家）。
     */
    public Long currentFarmerIdOrNull(Long userId) {
        Farmer farmer = findByUserId(userId);
        return isAudited(farmer) ? farmer.getId() : null;
    }

    /**
     * 校验目标果农存在且已认证，供买家发起会话使用。
     */
    public Farmer requireAvailableFarmer(Long farmerId) {
        Farmer farmer = farmerMapper.selectById(farmerId);
        if (!isAudited(farmer)) {
            throw new BizException(ErrorCode.IM_TARGET_FARMER_INVALID);
        }
        return farmer;
    }

    private boolean isAudited(Farmer farmer) {
        return farmer != null && farmer.getAuditStatus() != null && farmer.getAuditStatus() == 1;
    }
}
