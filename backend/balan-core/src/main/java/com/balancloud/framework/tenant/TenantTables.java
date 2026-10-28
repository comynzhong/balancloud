package com.balancloud.framework.tenant;

import java.util.Set;

/**
 * 参与租户隔离的表清单，与 Flyway V13 中加了 tenant_id 列的表一一对应。
 *
 * <p>sys_menu 保持全局：菜单是平台功能目录，新租户建号后如果也按租户过滤，侧边栏会直接空掉。
 * sys_tenant 是租户注册表本身，不参与过滤。
 */
public final class TenantTables {

    private static final Set<String> SCOPED = Set.of(
            "sys_user", "sys_role", "sys_user_role", "sys_role_menu", "sys_user_station",
            "sys_oper_log", "sys_logininfor",
            "sys_dict_type", "sys_dict_data", "sys_message", "sys_message_read",
            "station", "charger", "charger_port", "sim_config",
            "rate_strategy", "rate_period", "charge_order", "order_rate_detail",
            "order_manual_log",
            "device_command", "alert_record", "work_order", "work_order_record",
            "app_user", "app_vehicle", "app_user_blacklist", "order_refund",
            "ocpp_transaction");

    private TenantTables() {
    }

    public static boolean isScoped(String tableName) {
        return tableName != null && SCOPED.contains(tableName.toLowerCase().replace("`", ""));
    }

    public static Set<String> scoped() {
        return SCOPED;
    }
}
