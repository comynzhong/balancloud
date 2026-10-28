package com.balancloud.staff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StaffPortVo {

    private Integer portNumber;
    private String portStatus;
    private String plugged;
    private LocalDateTime occupiedSince;
    private String occupancyOrderNo;
    private String currentOrderNo;
    private BigDecimal ratedPowerKw;
    private BigDecimal ratedCurrentA;
    private LocalDateTime portLastMetricAt;

    private Double liveVoltage;
    private Double liveCurrentA;
    private Double liveTemperature;
    private Double livePowerKw;

    /** 没有进行中的订单时为 null，不伪造“预计可用时间”（§50 D4）。 */
    private StaffCurrentOrderVo currentOrder;
}
