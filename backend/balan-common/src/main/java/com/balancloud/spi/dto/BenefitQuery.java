package com.balancloud.spi.dto;

import java.math.BigDecimal;

public record BenefitQuery(Long appUserId,
                           Long tenantId,
                           Long stationId,
                           Long chargerId,
                           Integer portNo,
                           BigDecimal baseAmount) {
}
