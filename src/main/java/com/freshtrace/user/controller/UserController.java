package com.freshtrace.user.controller;

import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.R;
import com.freshtrace.security.UserContext;
import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.LogoutDTO;
import com.freshtrace.user.dto.RefreshDTO;
import com.freshtrace.user.dto.RegisterDTO;
import com.freshtrace.user.dto.UpdateProfileDTO;
import com.freshtrace.user.service.UserService;
import com.freshtrace.user.vo.LoginVO;
import com.freshtrace.user.vo.RefreshVO;
import com.freshtrace.user.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(userService.login(dto));
    }

    @PostMapping("/refresh")
    public R<RefreshVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return R.ok(userService.refresh(dto.getRefreshToken()));
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, @RequestBody LogoutDTO dto) {
        userService.logout(resolveToken(request), dto.getRefreshToken());
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.getProfile(UserContext.get().getUserId()));
    }

    @PutMapping("/me")
    public R<UserVO> updateMe(@Valid @RequestBody UpdateProfileDTO dto) {
        return R.ok(userService.updateProfile(UserContext.get().getUserId(), dto));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
