package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.dto.ConfigItemRequest;
import com.balancloud.system.dto.ConfigUpdateRequest;
import com.balancloud.system.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/config")
public class SysConfigController {

    private final SystemConfigService configService;

    public SysConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @GetMapping
    public AjaxResult groups() {
        return AjaxResult.success(configService.groups());
    }

    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @Log(title = "系统配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@Valid @RequestBody ConfigUpdateRequest request) {
        configService.update(request.getValues());
        return AjaxResult.success();
    }

    /**
     * 扩展项通用读取：只含 ext.* 项，供后台其他模块按需取值。
     */
    @GetMapping("/values")
    public AjaxResult extensionValues() {
        return AjaxResult.success(configService.extensionValues());
    }

    @PreAuthorize("@ss.hasPermi('system:config:manage')")
    @Log(title = "系统配置项", businessType = BusinessType.INSERT)
    @PostMapping("/items")
    public AjaxResult createItem(@Valid @RequestBody ConfigItemRequest request) {
        configService.createItem(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:config:manage')")
    @Log(title = "系统配置项", businessType = BusinessType.UPDATE)
    @PutMapping("/items/{configKey}")
    public AjaxResult updateItem(@PathVariable String configKey, @Valid @RequestBody ConfigItemRequest request) {
        configService.updateItem(configKey, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('system:config:manage')")
    @Log(title = "系统配置项", businessType = BusinessType.DELETE)
    @DeleteMapping("/items/{configKey}")
    public AjaxResult deleteItem(@PathVariable String configKey) {
        configService.deleteItem(configKey);
        return AjaxResult.success();
    }
}
