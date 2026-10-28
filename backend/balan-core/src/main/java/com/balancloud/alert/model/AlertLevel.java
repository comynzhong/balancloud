package com.balancloud.alert.model;

public enum AlertLevel {
    CRITICAL,
    MAJOR,
    INFO;

    /** 严重程度：CRITICAL > MAJOR > INFO */
    public int rank() {
        return switch (this) {
            case CRITICAL -> 3;
            case MAJOR -> 2;
            case INFO -> 1;
        };
    }

    public static AlertLevel max(AlertLevel left, AlertLevel right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.rank() >= right.rank() ? left : right;
    }

    /**
     * 兼容厂商上报的 WARN / WARNING / FATAL 等级别写法。
     */
    public static AlertLevel fromText(String value) {
        if (value == null) {
            return MAJOR;
        }
        String text = value.trim().toUpperCase();
        return switch (text) {
            case "CRITICAL", "FATAL", "ERROR", "EMERGENCY" -> CRITICAL;
            case "WARN", "WARNING", "MAJOR", "ALARM" -> MAJOR;
            case "INFO", "NOTICE", "MINOR" -> INFO;
            default -> MAJOR;
        };
    }
}
