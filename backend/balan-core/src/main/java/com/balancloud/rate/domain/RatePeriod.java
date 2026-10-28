package com.balancloud.rate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("rate_period")
public class RatePeriod {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long strategyId;

    private String periodType;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal timePrice;

    private BigDecimal energyPrice;

    private BigDecimal servicePrice;

    private String serviceUnit;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String delFlag;
}
