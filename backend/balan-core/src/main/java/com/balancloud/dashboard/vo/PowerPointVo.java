package com.balancloud.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PowerPointVo {

    /** 采样时间，epoch 秒，对齐到 10 秒栅格 */
    private long ts;

    private BigDecimal powerKw;

    private long chargingPorts;
}
