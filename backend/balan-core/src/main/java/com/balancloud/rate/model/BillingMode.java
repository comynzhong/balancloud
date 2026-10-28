package com.balancloud.rate.model;

public enum BillingMode {
    TIME,
    ENERGY,
    MIXED;

    public static BillingMode parse(String value) {
        try {
            return BillingMode.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("计费模式不合法");
        }
    }

    public boolean chargesTime() {
        return this != ENERGY;
    }

    public boolean chargesEnergy() {
        return this != TIME;
    }
}
