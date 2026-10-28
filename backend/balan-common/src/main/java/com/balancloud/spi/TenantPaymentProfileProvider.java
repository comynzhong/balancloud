package com.balancloud.spi;

import com.balancloud.spi.dto.TenantPaymentProfile;

/**
 * 租户收款配置读取点（§51.5）：core 只依赖这个接口做通道路由，平台版实现读
 * {@code tenant_payment_config}，社区版返回 {@code null}。
 */
public interface TenantPaymentProfileProvider {

    /**
     * @param tenantId 当前租户
     * @param channel  渠道键，如 {@code WECHAT}
     * @return 已启用的配置快照；未配置、已停用或渠道不支持都返回 {@code null}
     */
    TenantPaymentProfile find(Long tenantId, String channel);
}
