package com.balancloud.rate.model;

public enum ServiceUnit {
    KWH,
    HOUR;

    public static ServiceUnit parse(String value) {
        try {
            return ServiceUnit.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("服务费计价单位不合法");
        }
    }
}
