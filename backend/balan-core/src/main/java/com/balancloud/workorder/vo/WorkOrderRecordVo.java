package com.balancloud.workorder.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderRecordVo {

    private Long id;
    private String action;
    private String content;
    private String operator;
    private LocalDateTime createTime;
}
