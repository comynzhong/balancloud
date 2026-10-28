package com.balancloud.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** 全站经营趋势的一个统计刻度（按天或按月）。 */
@Data
@AllArgsConstructor
public class BusinessTrendPointVo {

    /** 刻度键：yyyy-MM-dd 或 yyyy-MM */
    private String key;

    /** 图表 X 轴标签 */
    private String label;

    private long orderCount;

    private BigDecimal energyKwh;

    private BigDecimal amount;

    private BigDecimal energyFee;

    private BigDecimal serviceFee;
}
