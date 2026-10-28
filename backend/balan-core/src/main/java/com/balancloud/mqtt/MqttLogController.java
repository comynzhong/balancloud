package com.balancloud.mqtt;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mqtt")
public class MqttLogController {

    private final MqttLogService logService;

    public MqttLogController(MqttLogService logService) {
        this.logService = logService;
    }

    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/logs")
    public AjaxResult logs(@RequestParam(required = false) String deviceNo,
                           @RequestParam(defaultValue = "50") int limit) {
        return AjaxResult.success(logService.list(deviceNo, limit));
    }

    @PreAuthorize("@ss.hasPermi('simulator:config')")
    @Log(title = "MQTT 报文", businessType = BusinessType.CLEAN)
    @DeleteMapping("/logs")
    public AjaxResult clear() {
        logService.clear();
        return AjaxResult.success();
    }
}
