package com.balancloud.app.controller;

import com.balancloud.app.dto.BlacklistQuery;
import com.balancloud.app.dto.BlacklistRequest;
import com.balancloud.app.dto.BlacklistUpdateRequest;
import com.balancloud.app.service.AppBlacklistService;
import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
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
@RequestMapping("/api/v1/app-blacklist")
public class AppBlacklistController {

    private final AppBlacklistService blacklistService;

    public AppBlacklistController(AppBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @PreAuthorize("@ss.hasPermi('blacklist:list')")
    @GetMapping
    public TableDataInfo list(BlacklistQuery query) {
        return blacklistService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('blacklist:add')")
    @GetMapping("/users")
    public AjaxResult userOptions(@RequestParam(required = false) String keyword) {
        return AjaxResult.success(blacklistService.userOptions(keyword));
    }

    @PreAuthorize("@ss.hasPermi('blacklist:add')")
    @Log(title = "黑名单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody BlacklistRequest request) {
        blacklistService.create(request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('blacklist:edit')")
    @Log(title = "黑名单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody BlacklistUpdateRequest request) {
        blacklistService.update(id, request);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('blacklist:remove')")
    @Log(title = "黑名单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        blacklistService.delete(id);
        return AjaxResult.success();
    }
}
