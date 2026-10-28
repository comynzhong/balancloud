package com.balancloud.staff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StaffAlertVo {

    private Long alertId;
    private Integer alertPortNo;
    private String alertCode;
    private String alertName;
    private String alertSeverity;
    private String alertStatus;
    private String alertContent;
    private BigDecimal alertMetricValue;
    private BigDecimal alertThresholdValue;
    private LocalDateTime alertOccurredAt;
    private LocalDateTime alertLastOccurredAt;
    /** 已确认的告警要显示谁确认的，避免现场重复处置。 */
    private String alertAcknowledgedBy;
}
