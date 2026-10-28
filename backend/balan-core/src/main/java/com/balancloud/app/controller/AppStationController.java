package com.balancloud.app.controller;

import com.balancloud.app.service.AppStationService;
import com.balancloud.common.core.AjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppStationController {

    private final AppStationService stationService;

    public AppStationController(AppStationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping("/stations")
    public AjaxResult list(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) Double latitude,
                           @RequestParam(required = false) Double longitude) {
        return AjaxResult.success(stationService.list(keyword, latitude, longitude));
    }

    @GetMapping("/stations/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(stationService.detail(id));
    }

    @GetMapping("/stations/{id}/rate")
    public AjaxResult rate(@PathVariable Long id) {
        return AjaxResult.success(stationService.rateFor(id));
    }

    @GetMapping("/chargers/by-no/{deviceNo}")
    public AjaxResult scan(@PathVariable String deviceNo) {
        return AjaxResult.success(stationService.scan(deviceNo));
    }
}
