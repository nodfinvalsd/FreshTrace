package com.freshtrace.user.service;

import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.RegisterDTO;
import com.freshtrace.user.dto.UpdateProfileDTO;
import com.freshtrace.user.vo.LoginVO;
import com.freshtrace.user.vo.RefreshVO;
import com.freshtrace.user.vo.UserVO;

public interface UserService {

    UserVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    RefreshVO refresh(String refreshToken);

    UserVO getProfile(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    void logout(String accessToken, String refreshToken);
}
