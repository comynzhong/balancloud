package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.dto.RoleRequest;
import com.balancloud.system.service.SysRoleAdminService;
import com.balancloud.system.service.SysRoleQueryService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/roles")
public class SystemRoleController {

    private final SysRoleQueryService roleQueryService;
    private final SysRoleAdminService roleAdminService;

    public SystemRoleController(SysRoleQueryService roleQueryService, SysRoleAdminService roleAdminService) {
        this.roleQueryService = roleQueryService;
        this.roleAdminService = roleAdminService;
    }

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping
    public AjaxResult list() {
        return AjaxResult.success(roleQueryService.list());
    }

    /** 可授权菜单树（含按钮），新建角色时不需要先有 roleId。 */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/menu-tree")
    public AjaxResult menuTree() {
        return AjaxResult.success(roleAdminService.grantableMenuTree());
    }

    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/{id}/menus")
    public AjaxResult menus(@PathVariable Long id) {
        return AjaxResult.success(roleQueryService.menuTree(id));
    }

    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody RoleRequest request) {
        return AjaxResult.success(roleAdminService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        roleAdminService.update(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@PathVariable Long id, @RequestParam String status) {
        roleAdminService.changeStatus(id, status);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:grant')")
    @Log(title = "角色授权", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/menus")
    public AjaxResult grantMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleAdminService.grantMenus(id, menuIds);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        roleAdminService.delete(id);
        return AjaxResult.success();
    }
}
