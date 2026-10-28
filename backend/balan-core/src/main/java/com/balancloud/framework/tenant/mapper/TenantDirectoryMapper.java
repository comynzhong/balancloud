package com.balancloud.framework.tenant.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户目录读取。sys_tenant 不在 {@link com.balancloud.framework.tenant.TenantTables} 白名单里，
 * 所以这条查询不会被拼上 tenant_id，正是跨租户循环需要的效果。
 */
public interface TenantDirectoryMapper {

    @Select("SELECT tenant_id FROM sys_tenant WHERE status = '0' AND del_flag = '0' ORDER BY tenant_id")
    List<Long> selectActiveTenantIds();

    /** 租户编码换租户号；编码不存在或已停用时返回 null，由调用方决定怎么报错。 */
    @Select("SELECT tenant_id FROM sys_tenant WHERE tenant_code = #{tenantCode} AND status = '0' AND del_flag = '0' LIMIT 1")
    Long selectTenantIdByCode(String tenantCode);

    /** 个人中心展示归属运营商名称。停用/已删的租户也要查得到，否则历史账号的归属会显示成空。 */
    @Select("SELECT tenant_name FROM sys_tenant WHERE tenant_id = #{tenantId} LIMIT 1")
    String selectTenantNameById(Long tenantId);
}
