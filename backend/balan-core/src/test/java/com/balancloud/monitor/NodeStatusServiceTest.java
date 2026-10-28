package com.balancloud.monitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeStatusServiceTest {

    @Test
    void jdbcTargetNeverCarriesCredentials() {
        assertThat(NodeStatusService.safeJdbcUrl(
                "jdbc:mysql://10.0.0.5:3306/balan?useSSL=false"))
                .isEqualTo("jdbc:mysql://10.0.0.5:3306/balan");
        assertThat(NodeStatusService.safeJdbcUrl(
                "jdbc:mysql://10.0.0.5:3306/balan?user=root&password=P@ssw0rd"))
                .doesNotContain("root", "P@ssw0rd");
        assertThat(NodeStatusService.safeJdbcUrl(null)).isEqualTo("JDBC");
    }

    @Test
    void brokerTargetKeepsHostButDropsUserInfo() {
        assertThat(NodeStatusService.safeBroker("tcp://admin:secret@10.0.0.5:1883"))
                .isEqualTo("tcp://10.0.0.5:1883");
        assertThat(NodeStatusService.safeBroker("tcp://10.0.0.5:1883"))
                .isEqualTo("tcp://10.0.0.5:1883");
    }
}
