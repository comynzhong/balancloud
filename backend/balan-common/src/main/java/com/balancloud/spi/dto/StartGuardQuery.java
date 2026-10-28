package com.balancloud.spi.dto;

public record StartGuardQuery(Long tenantId,
                              Long stationId,
                              Long chargerId,
                              Integer portNo,
                              Long appUserId) {
}
