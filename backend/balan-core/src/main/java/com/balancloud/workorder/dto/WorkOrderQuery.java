package com.balancloud.workorder.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class WorkOrderQuery {

    private Long stationId;

    private String status;

    private String priority;

    private String source;

    private Long assigneeId;

    private Boolean mine;

    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private long pageNum = 1;

    private long pageSize = 10;
}
