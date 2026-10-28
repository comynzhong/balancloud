package com.balancloud.workorder.model;

public enum WorkOrderStatus {
    OPEN,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public static WorkOrderStatus parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
