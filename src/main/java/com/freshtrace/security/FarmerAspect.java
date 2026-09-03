package com.freshtrace.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

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
    }
}
