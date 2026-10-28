package com.balancloud.device.protocol;

import com.balancloud.device.ocpp.service.OcppTransactionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 内置协议适配器与注册表。
 *
 * <p>放在自动配置里而不是组件扫描：注册表要按「所有 adapter bean」建索引，自动配置保证
 * 它在所有实现注册完之后才装配。适配器不加 {@code @ConditionalOnMissingBean}——协议是叠加的，
 * 重名由注册表在启动时直接报错，而不是让后注册的悄悄顶掉前一个。
 */
@AutoConfiguration
public class ProtocolAutoConfiguration {

    @Bean
    public BalanMqttAdapter balanMqttAdapter(ObjectMapper objectMapper) {
        return new BalanMqttAdapter(objectMapper);
    }

    /**
     * OCPP 适配器依赖交易表（STOP 指令要带 transactionId）。交易存储不在时跳过注册，
     * 让只装协议层的测试上下文仍然能独立启动；真实运行时存储是 {@code @Service}，必然存在。
     */
    @Bean
    @ConditionalOnBean(OcppTransactionStore.class)
    public Ocpp16JAdapter ocpp16JAdapter(ObjectMapper objectMapper, OcppTransactionStore transactionStore) {
        return new Ocpp16JAdapter(objectMapper, transactionStore);
    }

    @Bean
    public ProtocolAdapterRegistry protocolAdapterRegistry(List<DeviceProtocolAdapter> adapters) {
        return new ProtocolAdapterRegistry(adapters);
    }
}
