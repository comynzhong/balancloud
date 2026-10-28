package com.balancloud.workorder.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workOrderNo;

    private String title;

    private String description;

    private String source;

    private Long alertId;

    private Long stationId;

    private Long chargerId;

    private String deviceNo;

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

    @TableLogic
    private String delFlag;
}
