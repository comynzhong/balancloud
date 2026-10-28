package com.balancloud.order.flow;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 默认场景流程与注册表。
 *
 * <p>两轮实现用「同名 bean 覆盖」而不是按 {@code OrderFlow} 类型兜底：按类型兜底会让任何新增场景
 * 顺带把两轮默认实现挤掉。同名覆盖既保留替换口子，又让两个不同名实现同时注册时由
 * {@link OrderFlowRegistry} 在启动期直接失败。
 */
@AutoConfiguration
public class OrderFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "twoWheelOrderFlow")
    public TwoWheelOrderFlow twoWheelOrderFlow() {
        return new TwoWheelOrderFlow();
    }

    @Bean
    @ConditionalOnMissingBean(name = "acCarOrderFlow")
    public AcCarOrderFlow acCarOrderFlow() {
        return new AcCarOrderFlow();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dcCarOrderFlow")
    public DcCarOrderFlow dcCarOrderFlow() {
        return new DcCarOrderFlow();
    }

    @Bean
    public OrderFlowRegistry orderFlowRegistry(List<OrderFlow> flows) {
        return new OrderFlowRegistry(flows);
    }
}
