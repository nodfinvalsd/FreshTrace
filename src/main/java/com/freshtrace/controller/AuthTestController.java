package com.freshtrace.controller;

import com.freshtrace.common.R;
import com.freshtrace.security.LoginUser;
import com.freshtrace.security.RoleRequired;
import com.freshtrace.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth-test")
public class AuthTestController {

    @GetMapping("/any")
    public R<String> any() {
        LoginUser user = UserContext.get();
        return R.ok("userId=" + user.getUserId() + ", role=" + user.getRole());
    }

    @GetMapping("/admin")
    @RoleRequired(role = 1)
    public R<String> admin() {
        return R.ok("admin ok");
    }
}
