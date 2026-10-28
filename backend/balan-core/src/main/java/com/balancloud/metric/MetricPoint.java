package com.balancloud.metric;

import java.time.LocalDateTime;

public record MetricPoint(
        long chargerId,
        long stationId,
        int portNo,
        LocalDateTime ts,
        double voltage,
        double currentA,
        double temperature,
        double powerKw,
        double energyTotal,
        int signalStrength,
        long tenantId
) {
}
