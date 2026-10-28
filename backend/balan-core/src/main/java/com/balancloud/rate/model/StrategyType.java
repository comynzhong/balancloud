package com.balancloud.rate.model;

public enum StrategyType {
    ENERGY,
    SERVICE;

    public static StrategyType parse(String value) {
        if (value == null || value.isBlank()) {
            return ENERGY;
        }
        try {
            return StrategyType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("策略类型不合法");
        }
    }
}
