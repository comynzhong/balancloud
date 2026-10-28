package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.dto.ProfileUpdateRequest;
import com.balancloud.system.service.AuthService;
import com.balancloud.system.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心：当前登录账号自己的资料与密码，不需要额外权限，也不接受 userId 参数。
 */
@RestController
@RequestMapping("/api/v1/system/profile")
public class ProfileController {

    private final AuthService authService;
    private final SysUserService userService;

    public ProfileController(AuthService authService, SysUserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @GetMapping
    public AjaxResult profile() {
        return AjaxResult.success(userService.profile());
    }

    @Log(title = "个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping
    public AjaxResult updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        userService.updateProfile(request);
        return AjaxResult.success();
    }

    @Log(title = "个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password")
    public AjaxResult changePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
        return AjaxResult.success("密码修改成功").put("token", authService.changePassword(oldPassword, newPassword));
    }
}
