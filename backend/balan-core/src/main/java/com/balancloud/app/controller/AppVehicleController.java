package com.balancloud.app.controller;

import com.balancloud.app.dto.VehicleRequest;
import com.balancloud.app.security.AppSecurityUtils;
import com.balancloud.app.service.AppVehicleService;
import com.balancloud.common.core.AjaxResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/vehicles")
public class AppVehicleController {

    private final AppVehicleService vehicleService;

    public AppVehicleController(AppVehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public AjaxResult list() {
        return AjaxResult.success(vehicleService.mine(AppSecurityUtils.currentUserId()));
    }

    @PostMapping
    public AjaxResult create(@Valid @RequestBody VehicleRequest request) {
        return AjaxResult.success(vehicleService.create(AppSecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return AjaxResult.success(vehicleService.update(id, AppSecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        vehicleService.delete(id, AppSecurityUtils.currentUserId());
        return AjaxResult.success();
    }

    @PutMapping("/{id}/default")
    public AjaxResult setDefault(@PathVariable Long id) {
        vehicleService.setDefault(id, AppSecurityUtils.currentUserId());
        return AjaxResult.success();
    }
}
