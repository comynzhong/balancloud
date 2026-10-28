package com.balancloud.app.controller;

import com.balancloud.app.dto.AppCodeLoginRequest;
import com.balancloud.app.dto.AppDevLoginRequest;
import com.balancloud.app.service.AppAuthService;
import com.balancloud.common.core.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final AppAuthService authService;

    public AppAuthController(AppAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AjaxResult login(@Valid @RequestBody AppCodeLoginRequest request) {
        return AjaxResult.success(authService.loginByCode(request.getCode(), request.getNickname(), request.getAvatar()));
    }

    @PostMapping("/dev-login")
    public AjaxResult devLogin(@Valid @RequestBody AppDevLoginRequest request) {
        return AjaxResult.success(authService.devLogin(request.getPhone()));
    }

    @GetMapping("/me")
    public AjaxResult me() {
        return AjaxResult.success(authService.me());
    }

    @PostMapping("/logout")
    public AjaxResult logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return AjaxResult.success("退出成功");
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (StringUtils.hasText(header)) {
            return header;
        }
        return request.getHeader("App-Token");
    }
}
