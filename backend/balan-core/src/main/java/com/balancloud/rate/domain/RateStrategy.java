package com.balancloud.rate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rate_strategy")
public class RateStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private String name;

    private String billingMode;

    private String strategyType;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Integer enabled;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}
