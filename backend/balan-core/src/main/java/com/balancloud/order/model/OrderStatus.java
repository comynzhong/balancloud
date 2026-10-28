package com.balancloud.order.model;

public enum OrderStatus {
    PENDING_START,
    CHARGING,
    STOPPING,
    COMPLETED,
    FAILED,
    ABNORMAL;

    public boolean isActive() {
        return this == PENDING_START || this == CHARGING || this == STOPPING;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    public static OrderStatus parse(String value) {
        try {
            return OrderStatus.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("订单状态不合法");
        }
    }
}
