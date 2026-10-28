package com.balancloud.spi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementContext(Long orderId,
                                String orderNo,
                                Long tenantId,
                                Long stationId,
                                Long appUserId,
                                String status,
                                String stopReason,
                                BigDecimal totalFee,
                                BigDecimal energyKwh,
                                LocalDateTime endTime) {
}
