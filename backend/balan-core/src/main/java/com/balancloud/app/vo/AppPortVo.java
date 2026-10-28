package com.balancloud.app.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppPortVo {

    private Integer portNo;
    private String status;
    private BigDecimal ratedCurrentA;
    private BigDecimal ratedPowerKw;
    private Double voltage;
    private Double currentA;
    private Double temperature;
    private Double powerKw;
    private Long metricTs;
}
