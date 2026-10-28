package com.balancloud.spi.dto;

import java.math.BigDecimal;

public record PayRequest(Long orderId,
                         String orderNo,
                         Long tenantId,
                         Long stationId,
                         Long appUserId,
                         BigDecimal amount,
                         String subject,
                         String operator) {
}
