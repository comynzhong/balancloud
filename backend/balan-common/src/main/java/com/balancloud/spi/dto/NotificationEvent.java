package com.balancloud.spi.dto;

import java.time.LocalDateTime;

/**
 * 通知事件。站内信渠道落库后会把生成的 messageId 回填给后续渠道。
 */
public record NotificationEvent(Long messageId,
                               String type,
                               String severity,
                               Long tenantId,
                               Long stationId,
                               String title,
                               String content,
                               String bizType,
                               String bizId,
                               LocalDateTime occurredAt) {

    public NotificationEvent withMessageId(Long id) {
        return new NotificationEvent(id, type, severity, tenantId, stationId, title, content,
                bizType, bizId, occurredAt);
    }
}
