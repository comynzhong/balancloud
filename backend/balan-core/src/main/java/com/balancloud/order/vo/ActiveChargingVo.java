package com.balancloud.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActiveChargingVo {

    private Long id;
    private String orderNo;
    private Long stationId;
    private String stationName;
    private Long chargerId;
    private String deviceNo;
    private String deviceName;
    private Integer portNo;
    private Long appUserId;
    private String appUserNickname;
    private String appUserPhone;
    private String appUserAvatar;
    private Long vehicleId;
    private String vehiclePlateNo;
    private String status;
    private LocalDateTime startTime;
    private Integer durationSeconds;
    private BigDecimal energyKwh;
    private BigDecimal totalFee;
    private Double currentPowerKw;
    private Integer maxDurationMinutes;
    private BigDecimal chargeLimitAmount;
    private LocalDateTime lastMetricAt;
}
