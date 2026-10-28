package com.balancloud.monitor;

import com.balancloud.common.core.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区版的「系统监控 / 节点状态」：四大中间件连通性 + 当前进程 JVM。
 * 与 /api/v1/cluster/node 的分工是：那个看 MQTT/WS 多实例身份，这个看依赖是否活着。
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class NodeStatusController {

    private final NodeStatusService nodeStatusService;

    public NodeStatusController(NodeStatusService nodeStatusService) {
        this.nodeStatusService = nodeStatusService;
    }

    @PreAuthorize("@ss.hasPermi('monitor:node:query')")
    @GetMapping("/node")
    public AjaxResult node() {
        return AjaxResult.success(nodeStatusService.snapshot());
    }
}
