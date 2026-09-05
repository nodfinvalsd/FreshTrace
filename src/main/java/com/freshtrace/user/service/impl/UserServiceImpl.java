package com.freshtrace.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.JwtUtils;
import com.freshtrace.security.JwtBlacklistService;
import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.RegisterDTO;
import com.freshtrace.user.dto.UpdateProfileDTO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import com.freshtrace.user.service.UserService;
import com.freshtrace.user.vo.LoginVO;
import com.freshtrace.user.vo.RefreshVO;
import com.freshtrace.user.vo.UserVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    public UserVO register(RegisterDTO dto) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername())) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone())) > 0) {
            throw new BizException(ErrorCode.PHONE_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return toVO(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, dto.getAccount())
                        .or()
                        .eq(User::getPhone, dto.getAccount())));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        return new LoginVO(accessToken, refreshToken,
                jwtUtils.getAccessExpireSeconds(), jwtUtils.getRefreshExpireSeconds());
    }

    @Override
    public RefreshVO refresh(String refreshToken) {
        Claims claims = jwtUtils.parseToken(refreshToken);
        if (!JwtUtils.TOKEN_TYPE_REFRESH.equals(claims.get("tokenType", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (jwtBlacklistService.isRefreshBlacklisted(claims.getId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String newAccessToken = jwtUtils.generateAccessToken(userId, user.getRole());
        return new RefreshVO(newAccessToken, jwtUtils.getAccessExpireSeconds());
    }

    @Override
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        Claims accessClaims = jwtUtils.parseToken(accessToken);
        jwtBlacklistService.blacklistAccess(accessClaims.getId(), remainingSeconds(accessClaims));
        if (StringUtils.hasText(refreshToken)) {
            Claims refreshClaims = jwtUtils.parseToken(refreshToken);
            jwtBlacklistService.blacklistRefresh(refreshClaims.getId(), remainingSeconds(refreshClaims));
        }
    }

    private long remainingSeconds(Claims claims) {
        return (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
