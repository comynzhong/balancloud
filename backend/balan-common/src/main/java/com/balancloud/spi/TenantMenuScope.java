package com.balancloud.spi;

import java.util.Set;

/**
 * 可授权菜单的租户级裁剪点：社区版不裁剪，标准版按会话租户绑定的套餐给出白名单。
 *
 * <p>只作用于「授权写入」这一条路（{@code grantableMenuTree()} 与 {@code grantMenus()}），
 * 路由与权限读取仍走 {@code sys_role_menu}，所以套餐变化不会让已授权角色凭空失权。
 */
public interface TenantMenuScope {

    /**
     * @param tenantId 当前会话租户
     * @return 该租户允许授权的菜单 id 集合；{@code null} 表示不裁剪
     */
    Set<Long> allowedMenuIds(Long tenantId);
}
