package com.balancloud.mqtt.model;

import lombok.Data;

import java.util.List;

@Data
public class MetricPayload {

    private String deviceId;
    private Long ts;
    private List<PortMetric> ports;
}
