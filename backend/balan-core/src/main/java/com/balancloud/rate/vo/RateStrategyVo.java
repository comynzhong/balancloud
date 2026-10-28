package com.balancloud.rate.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RateStrategyVo {

    private Long id;
    private Long stationId;
    private String stationName;
    private String name;
    private String billingMode;
    private String strategyType;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean current;
    private Integer enabled;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private List<RatePeriodVo> periods;
}
