package com.balancloud.dashboard.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public AjaxResult summary(@RequestParam(required = false) Long stationId) {
        return AjaxResult.success(dashboardService.summary(stationId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/power-trend")
    public AjaxResult powerTrend(@RequestParam(required = false) Long stationId,
                                 @RequestParam(defaultValue = "60") int minutes) {
        return AjaxResult.success(dashboardService.powerTrend(stationId, minutes));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/energy-trend")
    public AjaxResult energyTrend(@RequestParam(required = false) Long stationId) {
        return AjaxResult.success(dashboardService.energyTrend(stationId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/business-trend")
    public AjaxResult businessTrend(@RequestParam(required = false) Long stationId,
                                    @RequestParam(defaultValue = "7d") String range) {
        return AjaxResult.success(dashboardService.businessTrend(stationId, range));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/stations")
    public AjaxResult stations() {
        return AjaxResult.success(dashboardService.stationOverview());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/groups")
    public AjaxResult groups(@RequestParam(required = false) Long stationId) {
        return AjaxResult.success(dashboardService.groupOverview(stationId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/alert-stats")
    public AjaxResult alertStats(@RequestParam(required = false) Long stationId) {
        return AjaxResult.success(dashboardService.alertStats(stationId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/events")
    public AjaxResult events(@RequestParam(required = false) Long stationId,
                             @RequestParam(defaultValue = "20") int limit) {
        return AjaxResult.success(dashboardService.events(stationId, limit));
    }
}
