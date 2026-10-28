package com.balancloud.workorder.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderVo {

    private Long id;
    private String workOrderNo;
    private String title;
    private String description;
    private String source;
    private Long alertId;
    private String alertType;
    private String alertLevel;
    private Long stationId;
    private String stationName;
    private Long chargerId;
    private String deviceNo;
    private String deviceName;
    private Integer portNo;
    private String priority;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private String creator;
    private String resolution;
    private String closedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private long recordCount;
    private LocalDateTime lastRecordAt;
}
