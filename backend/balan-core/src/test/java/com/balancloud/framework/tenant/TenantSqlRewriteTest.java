package com.balancloud.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSqlRewriteTest {

    private final TenantLineInnerInterceptor interceptor =
            new TenantLineInnerInterceptor(new BalanTenantLineHandler());

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantConditionIsAddedForScopedTable() {
        assertThat(build("charger", null)).isEqualTo("tenant_id = 1");
    }

    @Test
    void aliasQualifiesTheTenantColumn() {
        assertThat(build("charger", "c")).isEqualTo("c.tenant_id = 1");
    }

    @Test
    void globalTableGetsNoCondition() {
        assertThat(build("sys_menu", null)).isNull();
    }

    @Test
    void overrideSwitchesTenantForCrossTenantJobs() {
        TenantContext.use(2L);

        assertThat(build("charge_order", null)).isEqualTo("tenant_id = 2");
    }

    private String build(String tableName, String alias) {
        Table table = new Table(tableName);
        if (alias != null) {
            table.setAlias(new Alias(alias));
        }
        Expression condition = interceptor.buildTableExpression(table, null, "WHERE");
        return condition == null ? null : condition.toString();
    }
}
