package com.balancloud.spi.defaults;

import com.balancloud.spi.TenantPaymentProfileProvider;
import com.balancloud.spi.dto.TenantPaymentProfile;

/**
 * 社区版没有租户收款配置：真实通道在社区版一律不可用，只有 mock 能跑。
 */
public class NoopTenantPaymentProfileProvider implements TenantPaymentProfileProvider {

    @Override
    public TenantPaymentProfile find(Long tenantId, String channel) {
        return null;
    }
}
