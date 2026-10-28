package com.balancloud.framework.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 启动自检：数据库里有 tenant_id 列却没登记进 {@link TenantTables} 的表，等于对它漏了隔离条件。
 *
 * <p>只告警不阻断启动，避免测试库尚未跑完迁移时把服务打死。
 */
@Component
public class TenantSchemaChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaChecker.class);

    private static final String SELECT_TENANT_TABLES =
            "SELECT table_name FROM information_schema.columns"
                    + " WHERE table_schema = DATABASE() AND column_name = 'tenant_id'";

    /** sys_tenant 是租户注册表本身，有主键列 tenant_id 但不参与过滤。 */
    private static final Set<String> EXEMPT = Set.of("sys_tenant");

    private final DataSource dataSource;

    public TenantSchemaChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        SortedSet<String> withColumn = new TreeSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_TENANT_TABLES);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                withColumn.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        } catch (SQLException e) {
            log.warn("Tenant schema self-check skipped: {}", e.getMessage());
            return;
        }

        SortedSet<String> unscoped = new TreeSet<>(withColumn);
        unscoped.removeAll(TenantTables.scoped());
        unscoped.removeAll(EXEMPT);
        if (!unscoped.isEmpty()) {
            log.warn("Tables with tenant_id but no tenant isolation, add them to TenantTables: {}", unscoped);
        }

        SortedSet<String> missingColumn = new TreeSet<>(TenantTables.scoped());
        missingColumn.removeAll(withColumn);
        if (!missingColumn.isEmpty()) {
            log.warn("TenantTables scopes tables without a tenant_id column, migration may be missing: {}", missingColumn);
        }

        if (unscoped.isEmpty() && missingColumn.isEmpty()) {
            log.info("Tenant schema self-check passed: {} scoped table(s)", TenantTables.scoped().size());
        }
    }
}
