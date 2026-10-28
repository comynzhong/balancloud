package com.balancloud.device.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.device.dto.OcppSimulatorRequest;
import com.balancloud.device.ocpp.service.OcppSimulatorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台内置 OCPP 模拟桩的启停入口。
 *
 * <p>GET 始终返回开关状态，前端据此决定是否渲染入口；开关关闭时 POST/DELETE 直接 403。
 * 生产环境必须保持 {@code balan.ocpp.simulator.enabled=false}——这是后端起子进程的能力。
 */
@RestController
@RequestMapping("/api/v1/device/ocpp/simulators")
public class OcppSimulatorController {

    private final OcppSimulatorService simulatorService;

    public OcppSimulatorController(OcppSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PreAuthorize("@ss.hasPermi('charger:list')")
    @GetMapping
    public AjaxResult status() {
        return AjaxResult.success(simulatorService.status());
    }

    @PreAuthorize("@ss.hasPermi('charger:edit')")
    @PostMapping
    public AjaxResult start(@RequestBody OcppSimulatorRequest request) {
        return AjaxResult.success(simulatorService.start(request));
    }

    @PreAuthorize("@ss.hasPermi('charger:edit')")
    @DeleteMapping
    public AjaxResult stop(@RequestParam String identity) {
        simulatorService.stop(identity);
        return AjaxResult.success("模拟桩已停止");
    }
}
