package com.balancloud.dashboard.vo;

import lombok.Data;

import java.util.List;

@Data
public class AlertStatsVo {

    private long todayTotal;
    private long openCount;
    private long acknowledgedCount;
    private List<NameCountVo> byType;
    private List<NameCountVo> byLevel;
}
