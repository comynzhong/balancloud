package com.balancloud.device.protocol;

import com.balancloud.alert.model.AlertLevel;
import com.balancloud.alert.model.AlertType;
import com.balancloud.alert.service.AlertService;
import com.balancloud.alert.service.MetricAlertEvaluator;
import com.balancloud.command.service.CommandService;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.service.DeviceRouting;
import com.balancloud.device.service.DeviceTenantResolver;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.metric.MetricIngestService;
import com.balancloud.mqtt.DeviceMessageHandler;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.MqttDedupService;
import com.balancloud.mqtt.MqttLogService;
import com.balancloud.device.ocpp.service.OcppOrderBridge;
import com.balancloud.order.service.ChargingService;
import com.balancloud.order.service.OccupancyService;
import com.balancloud.support.TableInfoSupport;
import com.balancloud.ws.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestProtocolReverseProofTest {

    private static final String DEVICE = "TEST-01";
    private static final long TENANT = 7L;

    private ChargerMapper chargerMapper;
    private ChargerPortMapper portMapper;
    private DeviceShadowService shadowService;
    private MetricIngestService metricIngestService;
    private ChargingService chargingService;
    private OccupancyService occupancyService;
    private AlertService alertService;
    private MetricAlertEvaluator alertEvaluator;
    private EventStreamService eventStreamService;
    private MqttLogService logService;
    private MqttDedupService dedupService;
    private DeviceTenantResolver tenantResolver;
    private DeviceMessageHandler handler;
    private final List<Object> published = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(Charger.class, ChargerPort.class);
    }

    @BeforeEach
    void setUp() {
        chargerMapper = mock(ChargerMapper.class);
        portMapper = mock(ChargerPortMapper.class);
        shadowService = mock(DeviceShadowService.class);
        metricIngestService = mock(MetricIngestService.class);
        CommandService commandService = mock(CommandService.class);
        chargingService = mock(ChargingService.class);
        occupancyService = mock(OccupancyService.class);
        alertService = mock(AlertService.class);
        alertEvaluator = mock(MetricAlertEvaluator.class);
        eventStreamService = mock(EventStreamService.class);
        logService = mock(MqttLogService.class);
        dedupService = mock(MqttDedupService.class);
        tenantResolver = mock(DeviceTenantResolver.class);
        handler = new DeviceMessageHandler(chargerMapper, portMapper, shadowService, metricIngestService,
                commandService, chargingService, occupancyService, alertService, alertEvaluator,
                eventStreamService, mock(StringRedisTemplate.class), mock(TenantTaskExecutor.class),
                mock(OcppOrderBridge.class));

        Charger charger = new Charger();
        charger.setId(9L);
        charger.setStationId(3L);
        charger.setDeviceNo(DEVICE);
        charger.setProtocol(TestProtocolAdapter.PROTOCOL);
        charger.setScene("TWOWHEEL");
        charger.setOnlineStatus("ONLINE");
        when(chargerMapper.selectByDeviceNo(DEVICE)).thenReturn(charger);

        ChargerPort port = new ChargerPort();
        port.setId(91L);
        port.setChargerId(9L);
        port.setPortNo(1);
        port.setStatus("IDLE");
        when(portMapper.selectByChargerId(9L)).thenReturn(List.of(port));

        when(tenantResolver.route(DEVICE)).thenReturn(new DeviceRouting(TENANT, TestProtocolAdapter.PROTOCOL, DEVICE));
        when(dedupService.firstSeen(any(), any(), any(), any(), any())).thenReturn(true);
    }

    private ApplicationEventPublisher publisher() {
        return event -> {
            published.add(event);
            if (event instanceof UplinkEvent uplink) {
                handler.onUplink(uplink);
            }
        };
    }

    private UplinkFrameRouter router(ProtocolAdapterRegistry registry) {
        return new UplinkFrameRouter(registry, tenantResolver, logService, dedupService, publisher());
    }

    private ProtocolAdapterRegistry registryWithTestProtocol() {
        return new ProtocolAdapterRegistry(List.of(new BalanMqttAdapter(new ObjectMapper()),
                new TestProtocolAdapter()));
    }

    private RawFrame frame(String topic, String payload) {
        return new RawFrame("mqtt", topic, payload.getBytes(StandardCharsets.UTF_8), Map.of(), Instant.now());
    }

    @Test
    void newProtocolRidesTheWholeUplinkChainWithoutCoreChanges() {
        UplinkFrameRouter router = router(registryWithTestProtocol());
        String metric = "METRIC|" + DEVICE + "|1793035000000|1,CHARGING,220.5,16.0,41.0,3.5,1000.0,22,1";

        router.route(DEVICE, "metric", frame("vendor/up/" + DEVICE, metric));

        verify(logService).record("IN", "vendor/up/" + DEVICE, DEVICE, metric);
        verify(dedupService).firstSeen(TENANT, DEVICE, TestProtocolAdapter.PROTOCOL, "metric", metric);
        // 影子、告警、计费三个下游都只认 PlatformEvent，没有一个地方知道 TEST_PROTOCOL 的报文长什么样
        verify(shadowService).save(eq(DEVICE), any());
        verify(alertEvaluator).evaluate(any(Charger.class), any(ChargerPort.class), any());
        verify(chargingService).onMetric(any(Charger.class), eq(1), any(), eq(1793035000000L));
        verify(metricIngestService).enqueue(any());
    }

    @Test
    void newProtocolVendorAlertAndOrderEventReachTheirExistingServices() {
        UplinkFrameRouter router = router(registryWithTestProtocol());

        router.route(DEVICE, "alert", frame("vendor/up/" + DEVICE,
                "ALERT|" + DEVICE + "|1|OVER_TEMP|MAJOR|枪温过高|65.5"));
        verify(alertService).raiseFromDevice(any(Charger.class), eq(1), eq(AlertType.OVER_TEMP),
                eq(AlertLevel.MAJOR), eq("枪温过高"), eq(new BigDecimal("65.5")));

        router.route(DEVICE, "order", frame("vendor/up/" + DEVICE,
                "ORDER|" + DEVICE + "|1|CO20261027000000001|USER_STOP|12.5|1793035000000"));
        verify(chargingService).handleOrderEvent(eq(DEVICE), any(PlatformEvent.OrderLifecycle.class));
    }

    @Test
    void unregisteredProtocolFramesAreDroppedWithoutWritingAnyShadow() {
        UplinkFrameRouter router = router(new ProtocolAdapterRegistry(List.of(new BalanMqttAdapter(new ObjectMapper()))));

        router.route(DEVICE, "metric", frame("vendor/up/" + DEVICE,
                "METRIC|" + DEVICE + "|1|1,CHARGING,220.5,16.0,41.0,3.5,1000.0,22,1"));

        verify(logService).record(eq("IN"), any(), eq(DEVICE), any());
        assertThat(published).isEmpty();
        verify(shadowService, never()).save(any(), any());
        verify(shadowService, never()).setOnline(any(), eq(true));
        verify(chargingService, never()).onMetric(any(), any(), any(), anyLong());
    }

    @Test
    void anInstanceWithoutTheNewProtocolFallsBackInsteadOfFailing() {
        ProtocolAdapterRegistry lean = new ProtocolAdapterRegistry(
                List.of(new BalanMqttAdapter(new ObjectMapper())));
        ProtocolAdapterRegistry full = registryWithTestProtocol();

        // 两个注册表实例 = 两个部署实例：少注册一个协议只影响新设备，不影响默认协议
        assertThat(lean.require("")).isInstanceOf(BalanMqttAdapter.class);
        assertThat(full.require(TestProtocolAdapter.PROTOCOL)).isInstanceOf(TestProtocolAdapter.class);
        assertThatThrownBy(() -> lean.require(TestProtocolAdapter.PROTOCOL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("协议未注册: TEST_PROTOCOL");
    }

    @Test
    void duplicateProtocolRegistrationFailsApplicationStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProtocolAutoConfiguration.class))
                .withUserConfiguration(DuplicateProtocols.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("协议重复注册: TEST_PROTOCOL");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateProtocols {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        TestProtocolAdapter testProtocolAdapter() {
            return new TestProtocolAdapter();
        }

        @Bean
        DeviceProtocolAdapter impostorAdapter() {
            return new DeviceProtocolAdapter() {
                @Override
                public String protocol() {
                    return TestProtocolAdapter.PROTOCOL;
                }

                @Override
                public ProtocolCapabilities capabilities() {
                    return new ProtocolCapabilities(false, false, false, false, false,
                            false, false, 1, ProtocolCapabilities.PortIdModel.PORT_NUMBER);
                }

                @Override
                public List<PlatformEvent> decode(RawFrame frame) {
                    return List.of();
                }

                @Override
                public RawFrame encode(PlatformCommand command) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
