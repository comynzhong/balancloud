package com.balancloud.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class EnergyPointVo {

    /** 0-23 小时 */
    private int hour;

    private long orderCount;

    private BigDecimal energyKwh;

    private BigDecimal revenue;
}
