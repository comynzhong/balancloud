package com.balancloud.spi.defaults;

import com.balancloud.spi.TenantConfigSource;

/**
 * 社区版没有租户级配置覆盖，一律沿用全局值。
 */
public class NoopTenantConfigSource implements TenantConfigSource {

    @Override
    public String overrideValue(Long tenantId, String configKey) {
        return null;
    }
}
