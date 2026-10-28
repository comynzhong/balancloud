package com.balancloud.order.controller;

import com.balancloud.command.dto.ThresholdRequest;
import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.order.dto.StartChargingRequest;
import com.balancloud.order.model.ChargingActor;
import com.balancloud.order.service.ChargingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chargers/{chargerId}/ports/{portNo}")
public class ChargingController {

    private final ChargingService chargingService;

    public ChargingController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    @PreAuthorize("@ss.hasPermi('charger:start')")
    @Log(title = "启动充电", businessType = BusinessType.UPDATE)
    @PostMapping("/start")
    public AjaxResult start(@PathVariable Long chargerId,
                            @PathVariable Integer portNo,
                            @Valid @RequestBody StartChargingRequest request) {
        return AjaxResult.success(chargingService.start(ChargingActor.operator(), chargerId, portNo, request));
    }

    @PreAuthorize("@ss.hasPermi('charger:stop')")
    @Log(title = "停止充电", businessType = BusinessType.UPDATE)
    @PostMapping("/stop")
    public AjaxResult stop(@PathVariable Long chargerId, @PathVariable Integer portNo) {
        return AjaxResult.success(chargingService.stop(ChargingActor.operator(), chargerId, portNo));
    }

    @PreAuthorize("@ss.hasPermi('charger:unlock')")
    @Log(title = "远程解锁", businessType = BusinessType.UPDATE)
    @PostMapping("/unlock")
    public AjaxResult unlock(@PathVariable Long chargerId, @PathVariable Integer portNo) {
        return AjaxResult.success(chargingService.unlock(chargerId, portNo));
    }

    @PreAuthorize("@ss.hasPermi('charger:threshold')")
    @Log(title = "保护阈值下发", businessType = BusinessType.UPDATE)
    @PutMapping("/threshold")
    public AjaxResult threshold(@PathVariable Long chargerId,
                                @PathVariable Integer portNo,
                                @Valid @RequestBody ThresholdRequest request) {
        return AjaxResult.success(chargingService.setThreshold(chargerId, portNo,
                request.getOverTempC(), request.getOverCurrentA()));
    }
}
