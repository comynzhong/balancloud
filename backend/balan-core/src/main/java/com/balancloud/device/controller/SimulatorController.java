package com.balancloud.device.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.dto.FaultInjectRequest;
import com.balancloud.device.dto.SimBatchRequest;
import com.balancloud.device.dto.SimConfigRequest;
import com.balancloud.device.dto.SimUnplugRequest;
import com.balancloud.device.service.SimulatorService;
import com.balancloud.system.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final SystemConfigService configService;

    public SimulatorController(SimulatorService simulatorService, SystemConfigService configService) {
        this.simulatorService = simulatorService;
        this.configService = configService;
    }

    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/status")
    public AjaxResult status() {
        return AjaxResult.success(simulatorService.status());
    }

    @PreAuthorize("@ss.hasPermi('simulator:config')")
    @Log(title = "仿真器配置", businessType = BusinessType.UPDATE)
    @PutMapping("/config")
    public AjaxResult updateConfig(@Valid @RequestBody SimConfigRequest request) {
        requireEnabled();
        return AjaxResult.success(simulatorService.updateConfig(request));
    }

    @PreAuthorize("@ss.hasPermi('simulator:start')")
    @Log(title = "仿真器", businessType = BusinessType.UPDATE)
    @PostMapping("/start")
    public AjaxResult start() {
        requireEnabled();
        return AjaxResult.success(simulatorService.start());
    }

    @PreAuthorize("@ss.hasPermi('simulator:stop')")
    @Log(title = "仿真器", businessType = BusinessType.UPDATE)
    @PostMapping("/stop")
    public AjaxResult stop() {
        return AjaxResult.success(simulatorService.stop());
    }

    @PreAuthorize("@ss.hasPermi('simulator:start')")
    @Log(title = "仿真设备", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult createBatch(@Valid @RequestBody SimBatchRequest request) {
        requireEnabled();
        return AjaxResult.success(simulatorService.createBatch(request));
    }

    @PreAuthorize("@ss.hasPermi('simulator:fault')")
    @Log(title = "仿真故障注入", businessType = BusinessType.OTHER)
    @PostMapping("/fault")
    public AjaxResult injectFault(@Valid @RequestBody FaultInjectRequest request) {
        requireEnabled();
        return AjaxResult.success(simulatorService.injectFault(request));
    }

    @PreAuthorize("@ss.hasPermi('charger:unplug')")
    @Log(title = "仿真设备", businessType = BusinessType.OTHER)
    @PostMapping("/unplug")
    public AjaxResult unplug(@Valid @RequestBody SimUnplugRequest request) {
        requireEnabled();
        return AjaxResult.success(simulatorService.unplug(request));
    }

    /**
     * 停止入口始终放行，避免关闭开关后仿真器无法收尾。
     */
    private void requireEnabled() {
        if (!configService.isSimulatorEnabled()) {
            throw new ServiceException(403, "仿真器功能已关闭，请先在系统配置中开启");
        }
    }
}
