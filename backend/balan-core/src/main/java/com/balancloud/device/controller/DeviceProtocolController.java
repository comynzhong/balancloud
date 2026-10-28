package com.balancloud.device.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.device.protocol.DeviceProtocolAdapter;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.protocol.ProtocolCapabilities;
import com.balancloud.order.flow.AutoStopRule;
import com.balancloud.order.flow.OrderFlow;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.model.RateSnapshot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议与场景的观测面：这一版听得懂哪些方言、每种方言允许平台做什么、每种场景的订单时序差异。
 *
 * <p>接新桩时先看这里：「设备上线了但没有事件」和「协议没注册」是两类完全不同的故障，
 * 前者查报文，后者查装配。
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceProtocolController {

    private final ProtocolAdapterRegistry protocolRegistry;
    private final OrderFlowRegistry flowRegistry;

    public DeviceProtocolController(ProtocolAdapterRegistry protocolRegistry,
                                    OrderFlowRegistry flowRegistry) {
        this.protocolRegistry = protocolRegistry;
        this.flowRegistry = flowRegistry;
    }

    /** 建档表单要选协议，所以设备管理权限同样可读；能力矩阵本身不含凭据。 */
    @PreAuthorize("@ss.hasPermi('charger:list') or @ss.hasPermi('simulator:list')")
    @GetMapping("/protocols")
    public AjaxResult protocols() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String protocol : protocolRegistry.available()) {
            DeviceProtocolAdapter adapter = protocolRegistry.require(protocol);
            ProtocolCapabilities capabilities = adapter.capabilities();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("protocol", protocol);
            row.put("remoteStart", capabilities.remoteStart());
            row.put("remoteStop", capabilities.remoteStop());
            row.put("unlock", capabilities.unlock());
            row.put("setThreshold", capabilities.setThreshold());
            row.put("ota", capabilities.ota());
            row.put("perPortMetering", capabilities.perPortMetering());
            row.put("bmsHandshake", capabilities.bmsHandshake());
            row.put("maxPorts", capabilities.maxPorts());
            row.put("portIdModel", capabilities.portIdModel().name());
            rows.add(row);
        }
        return AjaxResult.success(rows);
    }

    @PreAuthorize("@ss.hasPermi('simulator:list')")
    @GetMapping("/flows")
    public AjaxResult flows() {
        RateSnapshot empty = new RateSnapshot();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String scene : flowRegistry.available()) {
            OrderFlow flow = flowRegistry.require(scene);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scene", scene);
            row.put("startsOnCommandAck", flow.startsOnCommandAck());
            row.put("preChargePhases", flow.preChargePhases().stream().map(Enum::name).toList());
            row.put("autoStopRules", flow.autoStopRules(empty).stream().map(AutoStopRule::name).toList());
            row.put("stoppingTimeoutSeconds", flow.stoppingTimeout().getSeconds());
            rows.add(row);
        }
        return AjaxResult.success(rows);
    }
}
