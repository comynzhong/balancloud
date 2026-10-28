package com.balancloud.framework.config;

import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.ws.EventStreamService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 事件流的 Redis 订阅端：把其他实例广播过来的事件投给本节点的 WebSocket 连接。
 */
@Configuration
public class EventStreamListenerConfig {

    @Bean
    public RedisMessageListenerContainer eventStreamListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     EventStreamService eventStreamService,
                                                                     ClusterProperties clusterProperties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> eventStreamService.onRemoteEvent(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(clusterProperties.getWsChannel()));
        return container;
    }
}
