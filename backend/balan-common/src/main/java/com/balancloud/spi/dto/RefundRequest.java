package com.balancloud.spi.dto;

import java.math.BigDecimal;

public record RefundRequest(Long orderId,
                            String orderNo,
                            String refundNo,
                            Long tenantId,
                            Long stationId,
                            Long appUserId,
                            BigDecimal amount,
                            String reason,
                            String operator) {
}
