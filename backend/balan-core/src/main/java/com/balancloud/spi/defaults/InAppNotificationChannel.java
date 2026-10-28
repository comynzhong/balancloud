package com.balancloud.spi.defaults;

import com.balancloud.spi.NotificationChannel;
import com.balancloud.spi.dto.NotificationEvent;
import com.balancloud.system.domain.SysMessage;
import com.balancloud.system.mapper.SysMessageMapper;
import com.balancloud.ws.EventStreamService;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 社区版默认通知渠道：站内信落库 + WebSocket 推送。
 */
public class InAppNotificationChannel implements NotificationChannel {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final SysMessageMapper messageMapper;
    private final EventStreamService eventStreamService;

    public InAppNotificationChannel(SysMessageMapper messageMapper, EventStreamService eventStreamService) {
        this.messageMapper = messageMapper;
        this.eventStreamService = eventStreamService;
    }

    @Override
    public String key() {
        return "inapp";
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public NotificationEvent send(NotificationEvent event) {
        SysMessage message = new SysMessage();
        message.setMsgType(StringUtils.hasText(event.type()) ? event.type() : "SYSTEM");
        message.setSeverity(StringUtils.hasText(event.severity()) ? event.severity() : "INFO");
        message.setStationId(event.stationId());
        message.setTitle(truncate(event.title(), MAX_TITLE_LENGTH));
        message.setContent(truncate(event.content(), MAX_CONTENT_LENGTH));
        message.setBizType(event.bizType());
        message.setBizId(event.bizId());
        LocalDateTime occurredAt = event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt();
        message.setCreateTime(occurredAt);
        messageMapper.insert(message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.getId());
        payload.put("msgType", message.getMsgType());
        payload.put("severity", message.getSeverity());
        payload.put("stationId", message.getStationId());
        payload.put("title", message.getTitle());
        payload.put("createTime", message.getCreateTime());
        eventStreamService.publishAfterCommit("message", event.stationId(), payload);
        return event.withMessageId(message.getId());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
