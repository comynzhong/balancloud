package com.balancloud.alert.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private Long chargerId;

    private String deviceNo;

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private String delFlag;
}
