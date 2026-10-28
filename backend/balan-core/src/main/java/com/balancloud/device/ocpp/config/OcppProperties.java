package com.balancloud.device.ocpp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OCPP 传输层参数。都属于部署面，改完要重启，不放进 sys_config。
 */
@Data
@Component
@ConfigurationProperties(prefix = "balan.ocpp")
public class OcppProperties {

    /** 关掉后不注册 /ocpp 端点，适合完全不用 OCPP 的部署。 */
    private boolean enabled = true;

    /**
     * 开发默认关闭。开启后握手校验 Basic 凭据，凭据只从环境变量读，不入库。
     */
    private boolean authEnabled = false;

    private String basicUser = "";

    private String basicPassword = "";

    private String subprotocol = "ocpp1.6";

    private int maxFrameBytes = 256 * 1024;

    /** uniqueId → 指令的映射保留时长；超过这个时间没回执按指令超时处理。 */
    private Duration pendingTtl = Duration.ofSeconds(120);

    private Simulator simulator = new Simulator();

    @Data
    public static class Simulator {

        /** 本质是让后端起子进程，生产必须保持 false。 */
        private boolean enabled = false;

        private String python = "python";

        private String scriptPath = "tools/ocpp-sim/sim.py";

        /** 留空按 ws://127.0.0.1:${server.port} 推，反代或容器里再显式覆盖。 */
        private String url = "";
    }
}
