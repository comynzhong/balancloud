package com.balancloud.framework.cluster;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterLockInspectorServiceTest {

    @Test
    void singleTenantWindowKeepsTheBareTaskName() {
        Map<String, Object> item = ClusterLockInspectorService
                .parse("balan:window:", "window", "balan:window:command-retry");
        assertThat(item.get("task")).isEqualTo("command-retry");
        assertThat(item.get("tenantId")).isNull();
        assertThat(item.get("scope")).isEqualTo("window");
    }

    @Test
    void numericSuffixIsTheTenant() {
        Map<String, Object> item = ClusterLockInspectorService
                .parse("balan:leader:", "leader", "balan:leader:simulator:2");
        assertThat(item.get("task")).isEqualTo("simulator");
        assertThat(item.get("tenantId")).isEqualTo(2L);
    }

    @Test
    void nonNumericSuffixStaysPartOfTheTask() {
        Map<String, Object> item = ClusterLockInspectorService
                .parse("balan:window:", "window", "balan:window:morning-report-push:latest");
        assertThat(item.get("task")).isEqualTo("morning-report-push:latest");
        assertThat(item.get("tenantId")).isNull();
    }

    @Test
    void pushWindowIsReadAsItsOwnTaskPerTenant() {
        Map<String, Object> item = ClusterLockInspectorService
                .parse("balan:window:", "window", "balan:window:morning-report-push:3");
        assertThat(item.get("task")).isEqualTo("morning-report-push");
        assertThat(item.get("tenantId")).isEqualTo(3L);
    }
}
