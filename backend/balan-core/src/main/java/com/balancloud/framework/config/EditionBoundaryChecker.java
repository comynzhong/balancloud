package com.balancloud.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动自检：社区版库里出现了只该由闭源模块带来的东西，说明段界被越过了。
 *
 * <p>段界靠两处约定守住——core 迁移只用 ≤V99 段、平台菜单只用 ≥900 段（见 §35.8）。
 * 但直接往社区库手写 SQL、或误用 standard profile 起社区包，数据库层面都不会拒绝，
 * 于是这里在运行期补一次检查。只告警不阻断启动，和 {@code TenantSchemaChecker} 同一口径。
 */
@Component
public class EditionBoundaryChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EditionBoundaryChecker.class);

    /** 平台菜单段起点，与标准版迁移里的 menu_id 约定一致。 */
    private static final int PLATFORM_MENU_FLOOR = 900;

    /** 平台迁移段起点，与标准版启动参数追加的迁移段一致。 */
    private static final int PLATFORM_MIGRATION_FLOOR = 500;

    private static final String COUNT_PLATFORM_MENUS =
            "SELECT COUNT(*) FROM sys_menu WHERE menu_id >= " + PLATFORM_MENU_FLOOR;

    private static final String SELECT_APPLIED_VERSIONS =
            "SELECT version FROM flyway_schema_history WHERE success = 1";

    private final DataSource dataSource;
    private final String edition;

    public EditionBoundaryChecker(DataSource dataSource, @Value("${balan.edition:community}") String edition) {
        this.dataSource = dataSource;
        this.edition = edition;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"community".equalsIgnoreCase(edition)) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            checkMenus(connection);
            checkMigrations(connection);
        } catch (SQLException e) {
            log.warn("Edition boundary self-check skipped: {}", e.getMessage());
        }
    }

    private void checkMenus(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_PLATFORM_MENUS);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                log.warn("社区版库里存在 {} 条 menu_id >= {} 的平台菜单，社区壳不会渲染它们（前端按版本裁剪），"
                        + "但说明该库被标准版迁移或手工 SQL 写过", count, PLATFORM_MENU_FLOOR);
            }
        }
    }

    private void checkMigrations(Connection connection) throws SQLException {
        List<String> crossed = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_APPLIED_VERSIONS);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String version = rs.getString(1);
                if (isPlatformVersion(version)) {
                    crossed.add(version);
                }
            }
        }
        if (!crossed.isEmpty()) {
            log.warn("社区版库里已应用平台迁移段 {}（V{}+ 属标准版），社区包不会自己写入这些版本，"
                    + "通常是同一个库被标准版包迁移过，或有人手工执行了平台 SQL", crossed, PLATFORM_MIGRATION_FLOOR);
        }
        if (crossed.isEmpty()) {
            log.info("Edition boundary self-check passed: edition=community");
        }
    }

    private boolean isPlatformVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(version.split("[._]")[0]) >= PLATFORM_MIGRATION_FLOOR;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
