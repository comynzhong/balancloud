package com.balancloud.spi.defaults;

import com.balancloud.spi.TenantMenuScope;

import java.util.Set;

/**
 * 社区版没有套餐概念，可授权菜单不裁剪。
 */
public class UnrestrictedTenantMenuScope implements TenantMenuScope {

    @Override
    public Set<Long> allowedMenuIds(Long tenantId) {
        return null;
    }
}
