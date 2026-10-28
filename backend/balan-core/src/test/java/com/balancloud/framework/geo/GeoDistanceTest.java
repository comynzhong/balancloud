package com.balancloud.framework.geo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 期望值取自 miniapp/src/utils/geo.js 的 node 实测基准（tmp/geo-js-baseline.js），不是按公式推算。 */
class GeoDistanceTest {

    @Test
    void distanceMatchesJsBaseline() {
        assertThat(GeoDistance.distanceMeters(0d, 0d, 0d, 1d)).isEqualTo(111195L);
        assertThat(GeoDistance.distanceMeters(0d, 0d, 1d, 0d)).isEqualTo(111195L);
        assertThat(GeoDistance.distanceMeters(30.2741, 120.1551, 30.2741, 120.1551)).isEqualTo(0L);
        assertThat(GeoDistance.distanceMeters(30.2741, 120.1551, 30.28, 120.16)).isEqualTo(807L);
        assertThat(GeoDistance.distanceMeters(30.2741, 120.1551, 30.25, 120.15)).isEqualTo(2724L);
        assertThat(GeoDistance.distanceMeters(22.5431, 114.0579, 23.1291, 113.2644)).isEqualTo(104203L);
        assertThat(GeoDistance.distanceMeters(31.2304, 121.4737, 30.2741, 120.1551)).isEqualTo(164874L);
        assertThat(GeoDistance.distanceMeters(-33.8688, 151.2093, 30.2741, 120.1551)).isEqualTo(7842290L);
        assertThat(GeoDistance.distanceMeters(35d, 139d, 35d, 139.0001)).isEqualTo(9L);
    }

    @Test
    void decimalOverloadMatchesDoubleOverload() {
        assertThat(GeoDistance.distanceMeters(decimal("30.2741"), decimal("120.1551"),
                decimal("30.2800"), decimal("120.1600"))).isEqualTo(807L);
        assertThat(GeoDistance.distanceMeters(decimal("31.2304000"), decimal("121.4737000"),
                decimal("30.2741000"), decimal("120.1551000"))).isEqualTo(164874L);
    }

    @Test
    void missingCoordinateYieldsNull() {
        assertThat(GeoDistance.distanceMeters(30.2741, 120.1551, null, 120.16)).isNull();
        assertThat(GeoDistance.distanceMeters(decimal("30.2741"), decimal("120.1551"), null, decimal("120.16"))).isNull();
        assertThat(GeoDistance.formatDistance(null)).isEmpty();
    }

    @Test
    void formatMatchesJsToFixed() {
        assertThat(GeoDistance.formatDistance(0L)).isEqualTo("0m");
        assertThat(GeoDistance.formatDistance(1L)).isEqualTo("1m");
        assertThat(GeoDistance.formatDistance(235L)).isEqualTo("235m");
        assertThat(GeoDistance.formatDistance(999L)).isEqualTo("999m");
        assertThat(GeoDistance.formatDistance(1000L)).isEqualTo("1.0km");
        assertThat(GeoDistance.formatDistance(1004L)).isEqualTo("1.0km");
        assertThat(GeoDistance.formatDistance(1049L)).isEqualTo("1.0km");
        assertThat(GeoDistance.formatDistance(1050L)).isEqualTo("1.1km");
        assertThat(GeoDistance.formatDistance(1150L)).isEqualTo("1.1km");
        assertThat(GeoDistance.formatDistance(1250L)).isEqualTo("1.3km");
        assertThat(GeoDistance.formatDistance(1350L)).isEqualTo("1.4km");
        assertThat(GeoDistance.formatDistance(1500L)).isEqualTo("1.5km");
        assertThat(GeoDistance.formatDistance(2499L)).isEqualTo("2.5km");
        assertThat(GeoDistance.formatDistance(2500L)).isEqualTo("2.5km");
        assertThat(GeoDistance.formatDistance(111195L)).isEqualTo("111.2km");
        assertThat(GeoDistance.formatDistance(7842290L)).isEqualTo("7842.3km");
    }

    @Test
    void demoStationDistance() {
        assertThat(GeoDistance.distanceMeters(decimal("30.2084000"), decimal("120.2116000"),
                decimal("30.2741"), decimal("120.1551"))).isEqualTo(9101L);
        assertThat(GeoDistance.formatDistance(GeoDistance.distanceMeters(
                decimal("30.2084000"), decimal("120.2116000"), decimal("30.2741"), decimal("120.1551"))))
                .isEqualTo("9.1km");
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
