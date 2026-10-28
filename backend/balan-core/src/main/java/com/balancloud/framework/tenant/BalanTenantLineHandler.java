package com.balancloud.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 白名单式租户注入：只有 {@link TenantTables} 登记的表参与，其余表一律不加条件。
 */
public class BalanTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.get());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return !TenantTables.isScoped(tableName);
    }
}
