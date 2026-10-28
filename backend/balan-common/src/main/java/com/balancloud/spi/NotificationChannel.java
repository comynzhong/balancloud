package com.balancloud.spi;

import com.balancloud.spi.dto.NotificationEvent;

/**
 * 通知渠道扩展点：core 站内信落库后扇出，标准版追加短信/订阅消息/邮件渠道。
 */
public interface NotificationChannel {

    String key();

    /** 站内信为 0，其余渠道在其后执行；数值小者先。 */
    default int order() {
        return 100;
    }

    default boolean accepts(NotificationEvent event) {
        return true;
    }

    /** 返回可补全后的事件（如站内信写入后回填 messageId），供后续渠道继续消费。 */
    NotificationEvent send(NotificationEvent event);
}
