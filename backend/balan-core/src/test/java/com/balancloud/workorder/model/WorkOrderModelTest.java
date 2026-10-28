package com.balancloud.workorder.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderModelTest {

    @Test
    void parsesStatusCaseInsensitivelyAndRejectsUnknown() {
        assertThat(WorkOrderStatus.parse("processing")).isEqualTo(WorkOrderStatus.PROCESSING);
        assertThat(WorkOrderStatus.parse(" RESOLVED ")).isEqualTo(WorkOrderStatus.RESOLVED);
        assertThat(WorkOrderStatus.parse("bogus")).isNull();
        assertThat(WorkOrderStatus.parse(null)).isNull();
        assertThat(WorkOrderStatus.CLOSED.isTerminal()).isTrue();
        assertThat(WorkOrderStatus.RESOLVED.isTerminal()).isFalse();
    }

    @Test
    void parsesPriorityAndFallsBackOnUnknown() {
        assertThat(WorkOrderPriority.parse("high")).isEqualTo(WorkOrderPriority.HIGH);
        assertThat(WorkOrderPriority.parse("  ")).isNull();
        assertThat(WorkOrderPriority.parse("bogus")).isNull();
    }

    @Test
    void mapsAlertLevelToPriority() {
        assertThat(WorkOrderPriority.fromAlertLevel("CRITICAL")).isEqualTo(WorkOrderPriority.URGENT);
        assertThat(WorkOrderPriority.fromAlertLevel("major")).isEqualTo(WorkOrderPriority.HIGH);
        assertThat(WorkOrderPriority.fromAlertLevel("INFO")).isEqualTo(WorkOrderPriority.LOW);
        assertThat(WorkOrderPriority.fromAlertLevel("UNKNOWN")).isEqualTo(WorkOrderPriority.NORMAL);
        assertThat(WorkOrderPriority.fromAlertLevel(null)).isEqualTo(WorkOrderPriority.NORMAL);
    }
}
