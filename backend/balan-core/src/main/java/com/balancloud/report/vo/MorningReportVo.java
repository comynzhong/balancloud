package com.balancloud.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MorningReportVo {

    private LocalDate date;
    private LocalDateTime generatedAt;
    private String headline;

    private long orderCount;
    private long completedCount;
    private long abnormalCount;
    private long failedCount;
    private long unpaidCount;
    private BigDecimal energyKwh;
    private BigDecimal revenue;
    private BigDecimal energyFee;
    private BigDecimal serviceFee;
    private BigDecimal timeFee;
    private long refundCount;
    private BigDecimal refundAmount;

    private long newUserCount;
    private long activeUserCount;

    private long alertCount;
    private long criticalAlertCount;
    private long majorAlertCount;
    private long unresolvedAlertCount;

    private long deviceCount;
    private long onlineDeviceCount;
    private long offlineDeviceCount;
    private long portCount;
    private long faultPortCount;

    private Growth growth;
    private List<StationLine> topStations;

    @Data
    public static class Growth {
        private BigDecimal orderPercent;
        private BigDecimal energyPercent;
        private BigDecimal revenuePercent;
    }

    @Data
    public static class StationLine {
        private Long stationId;
        private String stationName;
        private long orderCount;
        private BigDecimal energyKwh;
        private BigDecimal revenue;
    }
}
