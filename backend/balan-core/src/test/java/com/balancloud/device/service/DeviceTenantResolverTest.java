package com.balancloud.device.service;

import com.balancloud.device.mapper.ChargerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTenantResolverTest {

    private ChargerMapper chargerMapper;
    private DeviceTenantResolver resolver;

    @BeforeEach
    void setUp() {
        chargerMapper = mock(ChargerMapper.class);
        resolver = new DeviceTenantResolver(chargerMapper);
    }

    @Test
    void messageIsAttributedToTheOwningTenant() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(2L));

        assertThat(resolver.resolve("SIM-01")).isEqualTo(2L);
    }

    /** device_no 只在租户内唯一：认不出归属就丢报文，猜错就是把设备写进别人的账本。 */
    @Test
    void ambiguousDeviceNoIsNotAttributed() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(Arrays.asList(1L, 2L));

        assertThat(resolver.resolve("SIM-01")).isNull();
    }

    @Test
    void unknownDeviceIsNotAttributed() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-404")).thenReturn(Collections.emptyList());

        assertThat(resolver.resolve("SIM-404")).isNull();
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void successfulLookupIsCached() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(1L));

        assertThat(resolver.resolve("SIM-01")).isEqualTo(1L);
        assertThat(resolver.resolve("SIM-01")).isEqualTo(1L);

        verify(chargerMapper).selectTenantIdsByDeviceNo("SIM-01");
    }

    /** 设备可能刚建号：缓存住空结果会让它永远认不出归属。 */
    @Test
    void emptyLookupIsNotCached() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01"))
                .thenReturn(Collections.emptyList(), List.of(3L));

        assertThat(resolver.resolve("SIM-01")).isNull();
        assertThat(resolver.resolve("SIM-01")).isEqualTo(3L);

        verify(chargerMapper, times(2)).selectTenantIdsByDeviceNo("SIM-01");
    }

    @Test
    void evictForcesAFreshLookup() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(1L), List.of(2L));

        assertThat(resolver.resolve("SIM-01")).isEqualTo(1L);
        resolver.evict("SIM-01");

        assertThat(resolver.resolve("SIM-01")).isEqualTo(2L);
    }

    @Test
    void lookupFailureIsTreatedAsUnattributable() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenThrow(new IllegalStateException("mysql down"));

        assertThat(resolver.resolve("SIM-01")).isNull();
    }

    @Test
    void routeCarriesTheNormalizedProtocolOfTheDevice() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(2L));
        when(chargerMapper.selectProtocolByDeviceNo("SIM-01")).thenReturn(" balan_mqtt ");

        assertThat(resolver.route("SIM-01")).isEqualTo(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
    }

    /** 早期建的设备 protocol 列为空：按内置协议处理，不是给未知设备猜协议。 */
    @Test
    void blankProtocolFallsBackToTheBuiltInOne() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(2L));
        when(chargerMapper.selectProtocolByDeviceNo("SIM-01")).thenReturn(null);

        assertThat(resolver.route("SIM-01"))
                .isEqualTo(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
    }

    @Test
    void protocolLookupFailureFallsBackToTheBuiltInOne() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(2L));
        when(chargerMapper.selectProtocolByDeviceNo("SIM-01")).thenThrow(new IllegalStateException("mysql down"));

        assertThat(resolver.route("SIM-01"))
                .isEqualTo(new DeviceRouting(2L, "BALAN_MQTT", "SIM-01"));
    }

    /** 归属不明的报文不查协议：查了也没人用，还白白多一次库。 */
    @Test
    void unattributableDeviceHasNoRoute() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-404")).thenReturn(Collections.emptyList());

        assertThat(resolver.route("SIM-404")).isNull();
        verify(chargerMapper, times(0)).selectProtocolByDeviceNo("SIM-404");
    }

    @Test
    void protocolLookupIsCached() {
        when(chargerMapper.selectTenantIdsByDeviceNo("SIM-01")).thenReturn(List.of(2L));
        when(chargerMapper.selectProtocolByDeviceNo("SIM-01")).thenReturn("BALAN_MQTT");

        resolver.route("SIM-01");
        resolver.route("SIM-01");

        verify(chargerMapper).selectProtocolByDeviceNo("SIM-01");
    }
}
