package com.balancloud.mqtt;

import com.balancloud.framework.cluster.ClusterNode;
import com.balancloud.framework.cluster.ClusterProperties;
import com.balancloud.mqtt.config.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttDedupServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MqttProperties properties;
    private MqttDedupService dedupService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties = new MqttProperties();
        ClusterProperties clusterProperties = new ClusterProperties();
        clusterProperties.setInstanceId("node-a");
        dedupService = new MqttDedupService(redisTemplate, properties, new ClusterNode(clusterProperties));
    }

    @Test
    void duplicateMetricFrameIsIgnored() {
        String payload = "{\"deviceId\":\"SIM-01\",\"ts\":1700000000000,\"ports\":[]}";
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true, false);

        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", payload)).isTrue();
        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", payload)).isFalse();
        assertThat(dedupService.skippedCount()).isEqualTo(1);
    }

    @Test
    void heartbeatIsNeverDeduped() {
        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "online", "{\"online\":true}")).isTrue();
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any());
    }

    /** 下行指令普通订阅，去重会让后排实例先把 Leader 实例那份吞掉，指令反而没人应答。 */
    @Test
    void downlinkCommandIsNeverDeduped() {
        String payload = "{\"cmdId\":\"c-1\",\"type\":\"START\",\"portNo\":1}";
        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "cmd", payload)).isTrue();
        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "cmd", payload)).isTrue();
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any());
    }

    @Test
    void differentDevicesDoNotShareOneWindow() {
        String payload = "{\"deviceId\":\"SIM-01\",\"ts\":1700000000000}";
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", payload);
        dedupService.firstSeen(1L, "SIM-02", "BALAN_MQTT", "metric", payload);

        verify(valueOperations).setIfAbsent(org.mockito.ArgumentMatchers.contains(":SIM-01:metric:"),
                anyString(), any(Duration.class));
        verify(valueOperations).setIfAbsent(org.mockito.ArgumentMatchers.contains(":SIM-02:metric:"),
                anyString(), any(Duration.class));
    }

    /** device_no 只在租户内唯一：两个租户的同名设备必须各占一把键，否则报文互吞。 */
    @Test
    void sameDeviceNoInTwoTenantsHasItsOwnWindow() {
        String payload = "{\"deviceId\":\"SIM-01\",\"ts\":1700000000000}";
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", payload);
        dedupService.firstSeen(2L, "SIM-01", "BALAN_MQTT", "metric", payload);

        verify(valueOperations).setIfAbsent(org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:1:SIM-01:"),
                anyString(), any(Duration.class));
        verify(valueOperations).setIfAbsent(org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:2:SIM-01:"),
                anyString(), any(Duration.class));
    }

    /** 反查不到租户的报文记 0 段：仍然进键，只是不并入任何真实租户的窗口。 */
    @Test
    void unattributableMessageUsesTheZeroTenantSlot() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        dedupService.firstSeen(null, "SIM-01", null, "metric", "{\"ts\":1}");

        verify(valueOperations).setIfAbsent(org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:0:SIM-01:"),
                anyString(), any(Duration.class));
    }

    /** 同一设备号换过协议时，两种方言的相同正文不能互相吞掉。 */
    @Test
    void protocolSegmentKeepsDialectsApartForTheSameDevice() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", "{\"ts\":1}");
        dedupService.firstSeen(1L, "SIM-01", "CLOUDCHARGE", "metric", "{\"ts\":1}");

        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:1:SIM-01:metric:BALAN_MQTT:"),
                anyString(), any(Duration.class));
        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:1:SIM-01:metric:CLOUDCHARGE:"),
                anyString(), any(Duration.class));
    }

    /** 归属不明时协议也未知，键里记 UNKNOWN 段，绝不并入任何已注册协议。 */
    @Test
    void unknownProtocolFallsBackToAnExplicitSegment() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        dedupService.firstSeen(1L, "SIM-01", null, "metric", "{\"ts\":1}");

        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.startsWith("balan:mqtt:dup:1:SIM-01:metric:UNKNOWN:"),
                anyString(), any(Duration.class));
    }

    @Test
    void redisOutageProcessesTheMessageAnyway() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", "{\"ts\":1}")).isTrue();
    }

    @Test
    void dedupeCanBeSwitchedOff() {
        properties.setDedupeEnabled(false);
        assertThat(dedupService.firstSeen(1L, "SIM-01", "BALAN_MQTT", "metric", "{\"ts\":1}")).isTrue();
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any());
    }
}
