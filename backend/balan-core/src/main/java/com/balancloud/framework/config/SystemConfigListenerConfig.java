package com.balancloud.framework.config;

import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.system.service.SystemConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 系统配置的跨实例失效订阅端：某个实例改了配置，其余实例重载内存缓存。
 * 与事件流一样，Redis 不可用时只是退回「本实例改了才生效」的旧行为，不影响写入。
 */
@Configuration
public class SystemConfigListenerConfig {

    @Bean
    public RedisMessageListenerContainer systemConfigListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       SystemConfigService configService,
                                                                       ClusterProperties clusterProperties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> configService.reloadFromPeer(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(clusterProperties.getConfigChannel()));
        return container;
    }
}
