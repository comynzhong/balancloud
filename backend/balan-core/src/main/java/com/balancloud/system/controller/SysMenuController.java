package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.dto.MenuQuery;
import com.balancloud.system.dto.MenuRequest;
import com.balancloud.system.service.SysMenuAdminService;
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
@RequestMapping("/api/v1/system/menu")
public class SysMenuController {

    private final SysMenuAdminService menuService;

    public SysMenuController(SysMenuAdminService menuService) {
        this.menuService = menuService;
    }

    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @GetMapping("/list")
    public AjaxResult list(MenuQuery query) {
        return AjaxResult.success(menuService.list(query));
    }

    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(required = false) Long excludeId) {
        return AjaxResult.success(menuService.options(excludeId));
    }

    @PreAuthorize("@ss.hasPermi('system:menu:query')")
    @GetMapping("/{menuId}")
    public AjaxResult detail(@PathVariable Long menuId) {
        return AjaxResult.success(menuService.detail(menuId));
    }

    @PreAuthorize("@ss.hasPermi('system:menu:add')")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody MenuRequest request) {
        return AjaxResult.success(menuService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{menuId}")
    public AjaxResult update(@PathVariable Long menuId, @Valid @RequestBody MenuRequest request) {
        menuService.update(menuId, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:menu:remove')")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public AjaxResult delete(@PathVariable Long menuId) {
        menuService.delete(menuId);
        return AjaxResult.success();
    }
}
