package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.dto.LoginBody;
import com.balancloud.system.captcha.SliderCaptchaService;
import com.balancloud.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    private final AuthService authService;
    private final SliderCaptchaService captchaService;

    public LoginController(AuthService authService, SliderCaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @PostMapping("/login")
    public AjaxResult login(@Valid @RequestBody LoginBody body, HttpServletRequest request) {
        return AjaxResult.success().put("token", authService.login(body, request));
    }

    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        return authService.getInfo();
    }

    @GetMapping("/getRouters")
    public AjaxResult getRouters() {
        return AjaxResult.success(authService.getRouters());
    }

    @Log(title = "用户退出", businessType = BusinessType.OTHER, saveRequestData = false, saveResponseData = false)
    @PostMapping("/logout")
    public AjaxResult logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return AjaxResult.success("退出成功");
    }

    @GetMapping("/captchaImage")
    public AjaxResult captchaImage() {
        return AjaxResult.success()
                .put("captchaEnabled", captchaService.isEnabled())
                .put("type", "slider");
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
