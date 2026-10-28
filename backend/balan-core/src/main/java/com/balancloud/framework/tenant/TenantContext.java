package com.balancloud.framework.tenant;

import com.balancloud.app.security.AppLoginUser;
import com.balancloud.framework.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前线程的租户归属。
 *
 * <p>定时任务和 MQTT 上行没有登录会话，取不到租户时落回默认租户而不抛异常：
 * 一条 SQL 少了租户条件就是跨租户泄漏，宁可查错租户也不能查全部租户。
 */
public final class TenantContext {

    public static final Long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 供跨租户任务在循环体内显式切换；传 null 或调用 {@link #clear()} 恢复默认解析。 */
    public static void use(Long tenantId) {
        if (tenantId == null) {
            OVERRIDE.remove();
        } else {
            OVERRIDE.set(tenantId);
        }
    }

    public static void clear() {
        OVERRIDE.remove();
    }

    public static Long get() {
        Long tenantId = OVERRIDE.get();
        if (tenantId != null) {
            return tenantId;
        }
        tenantId = sessionTenantId();
        return tenantId == null ? DEFAULT_TENANT_ID : tenantId;
    }

    private static Long sessionTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.getTenantId();
        }
        if (principal instanceof AppLoginUser appLoginUser) {
            return appLoginUser.getTenantId();
        }
        return null;
    }
}
