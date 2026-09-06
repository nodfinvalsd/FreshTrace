package com.freshtrace.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 果农权限切面。
 * <p>
 * 校验通过后把 farmerId 写入 {@link FarmerContext}，业务层（Service）直接取用，
 * 无需重复 selectOne(t_farmer)。请求结束由 {@code @After} 清理，无论正常返回还是异常。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class FarmerAspect {

    private final FarmerMapper farmerMapper;

    @Before("@annotation(com.freshtrace.security.FarmerRequired)")
    public void checkFarmer() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new AccessDeniedException("需要登录");
        }
        Farmer farmer = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getUserId, loginUser.getUserId()));
        if (farmer == null || farmer.getAuditStatus() == null || farmer.getAuditStatus() != 1) {
            throw new AccessDeniedException("需要果农认证");
        }
        FarmerContext.set(farmer.getId());
    }

    @After("@annotation(com.freshtrace.security.FarmerRequired)")
    public void clearFarmerContext() {
        FarmerContext.clear();
    }
}
