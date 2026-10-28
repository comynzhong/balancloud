package com.balancloud.alert.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertVo {

    private Long id;
    private Long stationId;
    private String stationName;
    private Long chargerId;
    private String deviceNo;
    private String deviceName;
    private Integer portNo;
    private String alertType;
    private String alertLevel;
    private String status;
    private String source;
    private String content;
    private BigDecimal metricValue;
    private BigDecimal thresholdValue;
    private LocalDateTime occurredAt;
    private LocalDateTime lastOccurredAt;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private Long workOrderId;
    private String workOrderNo;
    private String workOrderStatus;
}
