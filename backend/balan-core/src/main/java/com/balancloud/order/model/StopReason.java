package com.balancloud.order.model;

public enum StopReason {
    USER_STOP,
    DEVICE_STOP,
    FULL,
    TIME_LIMIT,
    AMOUNT_LIMIT,
    NO_LOAD,
    FAULT,
    DEVICE_OFFLINE,
    STOP_TIMEOUT,
    MANUAL_FORCE,
    START_FAILED;

    public boolean abnormal() {
        return this == FAULT || this == DEVICE_OFFLINE || this == STOP_TIMEOUT;
    }

    public static StopReason parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StopReason.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}
