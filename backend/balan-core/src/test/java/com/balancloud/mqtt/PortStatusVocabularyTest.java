package com.balancloud.mqtt;

import com.balancloud.alert.service.AlertService;
import com.balancloud.alert.service.MetricAlertEvaluator;
import com.balancloud.command.service.CommandService;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.PortSample;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.metric.MetricIngestService;
import com.balancloud.order.flow.OccupyDecision;
import com.balancloud.order.flow.PreChargePhase;
import com.balancloud.device.ocpp.service.OcppOrderBridge;
import com.balancloud.order.service.ChargingService;
import com.balancloud.order.service.OccupancyService;
import com.balancloud.support.TableInfoSupport;
import com.balancloud.ws.EventStreamService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §45.5 / §46.4 第 4 条：{@code charger_port.status} 的取值集合固定为
 * IDLE / CHARGING / OCCUPIED / FAULT 四个。
 *
 * <p>握手、鉴权、BMS 协商这些半状态只允许进设备影子；厂商报文里出现它们时，
 * 端口表必须按「有电流就在充电、没电流就空闲」归一，而不是把新词汇写进库。
 */
class PortStatusVocabularyTest {

    private static final String DEVICE = "BC-S12-001";
    private static final long CHARGER_ID = 9L;
    private static final Set<String> VOCABULARY = Set.of("IDLE", "CHARGING", "OCCUPIED", "FAULT");

    private ChargerPortMapper portMapper;
    private DeviceMessageHandler handler;

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(Charger.class, ChargerPort.class);
    }

    @BeforeEach
    void setUp() {
        ChargerMapper chargerMapper = mock(ChargerMapper.class);
        portMapper = mock(ChargerPortMapper.class);
        DeviceShadowService shadowService = mock(DeviceShadowService.class);
        MetricIngestService metricIngestService = mock(MetricIngestService.class);
        CommandService commandService = mock(CommandService.class);
        ChargingService chargingService = mock(ChargingService.class);
        OccupancyService occupancyService = mock(OccupancyService.class);
        AlertService alertService = mock(AlertService.class);
        MetricAlertEvaluator alertEvaluator = mock(MetricAlertEvaluator.class);
        EventStreamService eventStreamService = mock(EventStreamService.class);
        handler = new DeviceMessageHandler(chargerMapper, portMapper, shadowService, metricIngestService,
                commandService, chargingService, occupancyService, alertService, alertEvaluator,
                eventStreamService, mock(StringRedisTemplate.class), mock(TenantTaskExecutor.class),
                mock(OcppOrderBridge.class));

        Charger charger = new Charger();
        charger.setId(CHARGER_ID);
        charger.setStationId(3L);
        charger.setDeviceNo(DEVICE);
        charger.setProtocol("BALAN_MQTT");
        charger.setScene("TWOWHEEL");
        charger.setOnlineStatus("ONLINE");
        when(chargerMapper.selectByDeviceNo(DEVICE)).thenReturn(charger);

        ChargerPort port = new ChargerPort();
        port.setId(91L);
        port.setChargerId(CHARGER_ID);
        port.setPortNo(1);
        port.setStatus("IDLE");
        when(portMapper.selectByChargerId(CHARGER_ID)).thenReturn(List.of(port));
    }

    @Test
    void vendorStatusesCollapseIntoTheFourValueVocabulary() {
        metric("HANDSHAKING", 5.0, false);
        metric("PLUGGED", 0.0, false);
        metric("CHARGE", 0.0, false);
        metric("IDLE", 0.0, true);
        metric("FAULT", 0.0, false);

        ArgumentCaptor<ChargerPort> captor = ArgumentCaptor.forClass(ChargerPort.class);
        verify(portMapper, times(5)).updateById(captor.capture());

        assertThat(captor.getAllValues()).extracting(ChargerPort::getStatus)
                .containsExactly("CHARGING", "IDLE", "IDLE", "OCCUPIED", "FAULT");
        assertThat(captor.getAllValues()).extracting(ChargerPort::getStatus)
                .allMatch(VOCABULARY::contains);
    }

    @Test
    void preChargeHalfStatesCanNeverBeWrittenAsPortStatus() {
        for (PreChargePhase phase : PreChargePhase.values()) {
            assertThatThrownBy(() -> new OccupyDecision(phase.name(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("端口状态只能写");
        }
    }

    private void metric(String status, double currentA, boolean plugged) {
        handler.handle(new PlatformEvent.MetricReport(DEVICE, System.currentTimeMillis(),
                List.of(PortSample.of(1, status, 220.0, currentA, 40.0, 1.2, 100.0, 20, plugged))));
    }
}
