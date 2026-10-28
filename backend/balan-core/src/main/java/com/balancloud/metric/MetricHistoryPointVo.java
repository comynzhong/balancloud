package com.balancloud.metric;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricHistoryPointVo {

    private long ts;
    private Double voltage;
    private Double currentA;
    private Double temperature;
    private Double powerKw;
    private Double energyTotal;
}
