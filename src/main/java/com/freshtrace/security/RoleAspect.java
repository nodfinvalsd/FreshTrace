package com.freshtrace.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RoleAspect {

    @Before("@annotation(roleRequired)")
    public void checkRole(RoleRequired roleRequired) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || !loginUser.getRole().equals(roleRequired.role())) {
            throw new AccessDeniedException("无权限访问");
        }
    }
}
