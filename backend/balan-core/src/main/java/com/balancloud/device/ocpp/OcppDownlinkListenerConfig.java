package com.balancloud.device.ocpp;

import com.balancloud.framework.cluster.ClusterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * OCPP 下行的 Redis 订阅端：本实例没有充电桩会话时，由持会话的实例代发。
 */
@Configuration
public class OcppDownlinkListenerConfig {

    @Bean
    public RedisMessageListenerContainer ocppDownlinkListenerContainer(
            RedisConnectionFactory connectionFactory,
            OcppDownlinkBus downlinkBus,
            ClusterProperties clusterProperties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> downlinkBus.onRemoteMessage(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(clusterProperties.getOcppDownChannel()));
        return container;
    }
}
