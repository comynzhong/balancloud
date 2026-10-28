package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.system.dto.PasswordRequest;
import com.balancloud.system.dto.UserQuery;
import com.balancloud.system.dto.UserRequest;
import com.balancloud.system.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/users")
public class SystemUserController {

    private final SysUserService userService;

    public SystemUserController(SysUserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping
    public TableDataInfo list(UserQuery query) {
        return userService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(userService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody UserRequest request) {
        return AjaxResult.success(userService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        userService.update(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@PathVariable Long id, @RequestParam String status) {
        userService.changeStatus(id, status);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PostMapping("/{id}/reset-password")
    public AjaxResult resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest request) {
        userService.resetPassword(id, request.getPassword());
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        userService.remove(id);
        return AjaxResult.success();
    }
}
