package com.balancloud.device.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SimulatorStatusVo {

    private boolean enabled;
    private boolean running;
    private int intervalSeconds;
    private BigDecimal faultProbability;
    private long deviceCount;
    private long portCount;
    private boolean mqttConnected;
    private String brokerUrl;
    private LocalDateTime startedAt;
    private LocalDateTime lastPublishAt;
}
