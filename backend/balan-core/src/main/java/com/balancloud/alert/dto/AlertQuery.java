package com.balancloud.alert.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class AlertQuery {

    private Long stationId;

    private Long chargerId;

    private Integer portNo;

    private String alertType;

    private String alertLevel;

    private String status;

    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private long pageNum = 1;

    private long pageSize = 20;
}
