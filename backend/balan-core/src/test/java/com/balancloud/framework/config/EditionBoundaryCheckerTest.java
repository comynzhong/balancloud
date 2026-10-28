package com.balancloud.framework.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EditionBoundaryCheckerTest {

    private DataSource dataSource;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void standardEditionDoesNotQueryAnything() {
        new EditionBoundaryChecker(dataSource, "standard").run(null);
        verifyNoInteractions(connection);
    }

    @Test
    void communityEditionScansBothBoundaries() throws SQLException {
        PreparedStatement menuStatement = mock(PreparedStatement.class);
        ResultSet menuRows = mock(ResultSet.class);
        when(connection.prepareStatement(contains("sys_menu"))).thenReturn(menuStatement);
        when(menuStatement.executeQuery()).thenReturn(menuRows);
        when(menuRows.next()).thenReturn(true);
        when(menuRows.getInt(1)).thenReturn(31);

        PreparedStatement flywayStatement = mock(PreparedStatement.class);
        ResultSet versionRows = mock(ResultSet.class);
        when(connection.prepareStatement(contains("flyway_schema_history"))).thenReturn(flywayStatement);
        when(flywayStatement.executeQuery()).thenReturn(versionRows);
        when(versionRows.next()).thenReturn(true, true, true, false);
        when(versionRows.getString(1)).thenReturn("12", "518", "9");

        new EditionBoundaryChecker(dataSource, "community").run(null);

        verify(connection).prepareStatement(contains("sys_menu"));
        verify(connection).prepareStatement(contains("flyway_schema_history"));
    }

    /** 自检只是提醒运维，库连不上也不能把服务挡在启动阶段。 */
    @Test
    void databaseErrorsDoNotBlockStartup() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("database down"));
        assertThatCode(() -> new EditionBoundaryChecker(dataSource, "community").run(null))
                .doesNotThrowAnyException();
    }
}
