package com.balancloud.spi;

/**
 * 系统配置的租户级覆盖点：只覆盖 {@code sys_config} 里已存在的键，
 * 定义（类型、分组、区间、枚举选项、是否内置）仍然是全局的，由 core 校验。
 */
public interface TenantConfigSource {

    /**
     * @param tenantId   当前会话租户
     * @param configKey  配置键
     * @return 覆盖值；{@code null} 或空串表示沿用全局值
     */
    String overrideValue(Long tenantId, String configKey);
}
