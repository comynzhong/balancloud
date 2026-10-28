package com.balancloud.alert.model;

public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED;

    public static boolean isActive(String status) {
        return !RESOLVED.name().equals(status);
    }
}
