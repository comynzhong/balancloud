package com.balancloud.framework.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 大圆距离与展示格式化，口径与 miniapp/src/utils/geo.js 逐值一致。
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS = 6371000d;

    private GeoDistance() {
    }

    public static Long distanceMeters(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double dLat = rad(lat2 - lat1);
        double dLng = rad(lng2 - lng1);
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double a = sinLat * sinLat + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * sinLng * sinLng;
        return Math.round(2 * EARTH_RADIUS * Math.asin(Math.sqrt(a)));
    }

    public static Long distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        return distanceMeters(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    public static String formatDistance(Long meters) {
        if (meters == null) {
            return "";
        }
        if (meters < 1000) {
            return meters + "m";
        }
        return new BigDecimal(meters / 1000d).setScale(1, RoundingMode.HALF_UP).toPlainString() + "km";
    }

    private static double rad(double degrees) {
        return degrees * Math.PI / 180;
    }
}
