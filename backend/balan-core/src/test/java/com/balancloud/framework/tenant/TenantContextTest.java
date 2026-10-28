package com.balancloud.framework.tenant;

import com.balancloud.app.security.AppLoginUser;
import com.balancloud.framework.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void fallsBackToDefaultTenantWithoutSession() {
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    void readsTenantFromAdminSession() {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(authentication(loginUser));

        assertThat(TenantContext.get()).isEqualTo(7L);
    }

    @Test
    void readsTenantFromAppSession() {
        AppLoginUser loginUser = new AppLoginUser();
        loginUser.setTenantId(3L);
        SecurityContextHolder.getContext().setAuthentication(authentication(loginUser));

        assertThat(TenantContext.get()).isEqualTo(3L);
    }

    @Test
    void legacySessionWithoutTenantUsesDefault() {
        SecurityContextHolder.getContext().setAuthentication(authentication(new LoginUser()));

        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    void explicitOverrideBeatsSession() {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(7L);
        SecurityContextHolder.getContext().setAuthentication(authentication(loginUser));

        TenantContext.use(9L);
        assertThat(TenantContext.get()).isEqualTo(9L);

        TenantContext.clear();
        assertThat(TenantContext.get()).isEqualTo(7L);
    }

    @Test
    void whitelistLocksScopedTables() {
        assertThat(TenantTables.scoped()).containsExactlyInAnyOrder(
                "sys_user", "sys_role", "sys_user_role", "sys_role_menu", "sys_user_station",
                "sys_oper_log", "sys_logininfor",
                "sys_dict_type", "sys_dict_data",
                "station", "charger", "charger_port", "sim_config",
                "rate_strategy", "rate_period", "charge_order", "order_rate_detail",
                "device_command", "alert_record",
                "app_user", "app_vehicle", "order_refund", "app_user_blacklist",
                "order_manual_log", "sys_message", "sys_message_read",
                "work_order", "work_order_record", "ocpp_transaction");
    }

    @Test
    void globalTablesStayOutsideTheWhitelist() {
        assertThat(TenantTables.isScoped("sys_menu")).isFalse();
        assertThat(TenantTables.isScoped("sys_tenant")).isFalse();
        assertThat(TenantTables.isScoped("flyway_schema_history")).isFalse();
    }

    @Test
    void tableLookupIgnoresCaseAndQuotes() {
        assertThat(TenantTables.isScoped("CHARGER")).isTrue();
        assertThat(TenantTables.isScoped("`charge_order`")).isTrue();
    }

    private UsernamePasswordAuthenticationToken authentication(Object principal) {
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
