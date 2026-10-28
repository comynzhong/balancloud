package com.balancloud.mqtt.model;

import lombok.Data;

@Data
public class AlertPayload {

    private String deviceId;
    private Integer portNo;
    private String type;
    private String level;
    private String message;
    private Double value;
    private Long ts;
}
