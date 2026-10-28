package com.balancloud.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVo {

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
    private Long vehicleId;
    private String vehiclePlateNo;
    private Long rateStrategyId;
    private String rateStrategyName;
    private String billingMode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private BigDecimal startMeter;
    private BigDecimal endMeter;
    private BigDecimal energyKwh;
    private BigDecimal timeFee;
    private BigDecimal energyFee;
    private BigDecimal serviceFee;
    private Integer occupancySeconds;
    private BigDecimal occupancyFee;
    private BigDecimal totalFee;
    private Integer maxDurationMinutes;
    private BigDecimal chargeLimitAmount;
    private String status;
    private String paymentStatus;
    private String startMethod;
    private String vin;
    private String refundNo;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundTime;
    private String stopReason;
    private String stopMessage;
    private LocalDateTime paidAt;
    private String startCmdId;
    private String stopCmdId;
    private String createBy;
    private LocalDateTime createTime;
}
