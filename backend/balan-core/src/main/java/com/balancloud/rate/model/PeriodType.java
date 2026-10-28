package com.balancloud.rate.model;

public enum PeriodType {
    PEAK,
    FLAT,
    VALLEY;

    public static PeriodType parse(String value) {
        try {
            return PeriodType.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("时段类型不合法");
        }
    }
}
