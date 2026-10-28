package com.balancloud.app.controller;

import com.balancloud.app.dto.VehicleQuery;
import com.balancloud.app.dto.VehicleRequest;
import com.balancloud.app.service.AppVehicleService;
import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleAdminController {

    private final AppVehicleService vehicleService;

    public VehicleAdminController(AppVehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PreAuthorize("@ss.hasPermi('vehicle:list')")
    @GetMapping
    public TableDataInfo list(VehicleQuery query) {
        return vehicleService.adminList(query);
    }

    @PreAuthorize("@ss.hasPermi('vehicle:edit')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(vehicleService.adminDetail(id));
    }

    @PreAuthorize("@ss.hasPermi('vehicle:list')")
    @Log(title = "车辆管理", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void export(VehicleQuery query, HttpServletResponse response) {
        vehicleService.exportAdmin(query, response);
    }

    @PreAuthorize("@ss.hasPermi('vehicle:add')")
    @Log(title = "车辆管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody VehicleRequest request) {
        return AjaxResult.success(vehicleService.createAdmin(request));
    }

    @PreAuthorize("@ss.hasPermi('vehicle:edit')")
    @Log(title = "车辆管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return AjaxResult.success(vehicleService.updateAdmin(id, request));
    }

    @PreAuthorize("@ss.hasPermi('vehicle:remove')")
    @Log(title = "车辆管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        vehicleService.deleteAdmin(id);
        return AjaxResult.success();
    }
}
