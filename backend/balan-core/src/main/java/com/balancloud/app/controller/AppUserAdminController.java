package com.balancloud.app.controller;

import com.balancloud.app.dto.AppUserQuery;
import com.balancloud.app.service.AppUserAdminService;
import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app-users")
public class AppUserAdminController {

    private final AppUserAdminService userService;

    public AppUserAdminController(AppUserAdminService userService) {
        this.userService = userService;
    }

    @PreAuthorize("@ss.hasPermi('appuser:list')")
    @GetMapping
    public TableDataInfo list(AppUserQuery query) {
        return userService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('appuser:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(userService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('appuser:list')")
    @Log(title = "小程序用户", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void export(AppUserQuery query, HttpServletResponse response) {
        userService.export(query, response);
    }

    @PreAuthorize("@ss.hasPermi('appuser:edit')")
    @Log(title = "小程序用户", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@PathVariable Long id, @RequestParam String status) {
        userService.changeStatus(id, status);
        return AjaxResult.success();
    }
}
