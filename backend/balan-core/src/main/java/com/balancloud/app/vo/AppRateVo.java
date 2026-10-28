package com.balancloud.app.vo;

import lombok.Data;

import java.util.List;

@Data
public class AppRateVo {

    private boolean configured;
    private Long strategyId;
    private String strategyName;
    private String billingMode;
    private AppRatePeriodVo current;
    private List<AppRatePeriodVo> periods;
}
