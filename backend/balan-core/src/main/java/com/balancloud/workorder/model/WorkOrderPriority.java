package com.balancloud.workorder.model;

public enum WorkOrderPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static WorkOrderPriority parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WorkOrderPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static WorkOrderPriority fromAlertLevel(String level) {
        if (level == null) {
            return NORMAL;
        }
        return switch (level.trim().toUpperCase()) {
            case "CRITICAL", "FATAL", "ERROR", "EMERGENCY" -> URGENT;
            case "MAJOR", "WARN", "WARNING", "ALARM" -> HIGH;
            case "INFO", "NOTICE", "MINOR" -> LOW;
            default -> NORMAL;
        };
    }
}
