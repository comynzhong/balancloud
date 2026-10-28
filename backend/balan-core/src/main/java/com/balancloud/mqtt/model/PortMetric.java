package com.balancloud.mqtt.model;

import lombok.Data;

@Data
public class PortMetric {

    private Integer portNo;
    private String status;
    private Double voltage;
    private Double currentA;
    private Double temperature;
    private Double powerKw;
    private Double energyTotal;
    private Integer signalStrength;
    private Boolean plugged;
    private Long ts;
}
