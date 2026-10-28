package com.balancloud.framework.spi;

import com.balancloud.spi.NotificationChannel;
import com.balancloud.spi.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 通知扇出：站内信渠道排在最前，其余渠道依次消费同一个事件；单个渠道失败只记日志，不影响业务事务。
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = channels.stream()
                .sorted(Comparator.comparingInt(NotificationChannel::order)
                        .thenComparing(NotificationChannel::key))
                .toList();
    }

    public NotificationEvent dispatch(NotificationEvent event) {
        NotificationEvent current = event;
        for (NotificationChannel channel : channels) {
            try {
                if (channel.accepts(current)) {
                    NotificationEvent enriched = channel.send(current);
                    if (enriched != null) {
                        current = enriched;
                    }
                }
            } catch (Exception e) {
                log.warn("Notification channel {} failed on {} {}: {}",
                        channel.key(), event.bizType(), event.bizId(), e.getMessage());
            }
        }
        return current;
    }

    public List<NotificationChannel> channels() {
        return channels;
    }
}
