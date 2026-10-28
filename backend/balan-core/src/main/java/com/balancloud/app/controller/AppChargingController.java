package com.balancloud.app.controller;

import com.balancloud.app.service.AppChargingService;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.order.dto.StartChargingRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppChargingController {

    private final AppChargingService chargingService;

    public AppChargingController(AppChargingService chargingService) {
        this.chargingService = chargingService;
    }

    @PostMapping("/chargers/{chargerId}/ports/{portNo}/start")
    public AjaxResult start(@PathVariable Long chargerId,
                            @PathVariable Integer portNo,
                            @Valid @RequestBody StartChargingRequest request) {
        return AjaxResult.success(chargingService.start(chargerId, portNo, request));
    }

    @PostMapping("/orders/{orderId}/stop")
    public AjaxResult stop(@PathVariable Long orderId) {
        return AjaxResult.success(chargingService.stop(orderId));
    }
}
