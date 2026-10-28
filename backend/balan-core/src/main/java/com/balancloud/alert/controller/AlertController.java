package com.balancloud.alert.controller;

import com.balancloud.alert.dto.AlertQuery;
import com.balancloud.alert.service.AlertService;
import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PreAuthorize("@ss.hasPermi('alert:list')")
    @GetMapping
    public TableDataInfo list(AlertQuery query) {
        return alertService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('alert:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(alertService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('alert:list')")
    @Log(title = "告警记录", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void export(AlertQuery query, HttpServletResponse response) {
        alertService.export(query, response);
    }

    @PreAuthorize("@ss.hasPermi('alert:ack')")
    @Log(title = "告警管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/ack")
    public AjaxResult ack(@PathVariable Long id) {
        return AjaxResult.success(alertService.ack(id));
    }

    @PreAuthorize("@ss.hasPermi('alert:resolve')")
    @Log(title = "告警管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/resolve")
    public AjaxResult resolve(@PathVariable Long id) {
        return AjaxResult.success(alertService.resolve(id));
    }
}
