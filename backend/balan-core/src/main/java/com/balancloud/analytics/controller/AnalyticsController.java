package com.balancloud.analytics.controller;

import com.balancloud.analytics.service.DeviceAnalyticsService;
import com.balancloud.common.core.AjaxResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final DeviceAnalyticsService deviceAnalyticsService;

    public AnalyticsController(DeviceAnalyticsService deviceAnalyticsService) {
        this.deviceAnalyticsService = deviceAnalyticsService;
    }

    @PreAuthorize("@ss.hasPermi('analytics:device:list')")
    @GetMapping("/devices")
    public AjaxResult devices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long stationId) {
        return AjaxResult.success(deviceAnalyticsService.device(startDate, endDate, stationId));
    }
}
