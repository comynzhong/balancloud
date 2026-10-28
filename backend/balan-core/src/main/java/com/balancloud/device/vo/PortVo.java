package com.balancloud.device.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PortVo {

    private Integer portNo;
    private String status;
    private BigDecimal ratedCurrentA;
    private BigDecimal ratedPowerKw;
    private String orderNo;
    private Long appUserId;
    private String appUserNickname;
    private String appUserPhone;
    private String vehiclePlateNo;
    private LocalDateTime orderStartTime;
    private Integer orderDurationSeconds;
    private LocalDateTime lastMetricAt;

    private Double voltage;
    private Double currentA;
    private Double temperature;
    private Double powerKw;
    private Double energyTotal;
    private Integer signalStrength;
    private Long metricTs;
}
