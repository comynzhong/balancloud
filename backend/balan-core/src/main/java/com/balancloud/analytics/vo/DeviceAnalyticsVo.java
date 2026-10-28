package com.balancloud.analytics.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DeviceAnalyticsVo {

    private LocalDate startDate;
    private LocalDate endDate;
    private DeviceAnalyticsSummaryVo summary;
    private List<DeviceAnalyticsRowVo> devices;

    @Data
    public static class DeviceAnalyticsSummaryVo {
        private int chargerCount;
        private int portCount;
        private long orderCount;
        private BigDecimal chargingHours;
        private BigDecimal utilizationRate;
        private long faultOrders;
        private BigDecimal faultRate;
        private BigDecimal avgDurationMinutes;
        private BigDecimal energyKwh;
        private BigDecimal revenue;
        private long faultPorts;
    }

    @Data
    public static class DeviceAnalyticsRowVo {
        private Long chargerId;
        private String deviceNo;
        private String deviceName;
        private Long stationId;
        private String stationName;
        private int portCount;
        private long orderCount;
        private BigDecimal chargingHours;
        private BigDecimal utilizationRate;
        private long faultOrders;
        private BigDecimal faultRate;
        private BigDecimal avgDurationMinutes;
        private BigDecimal energyKwh;
        private BigDecimal revenue;
        private long faultPorts;
    }
}
