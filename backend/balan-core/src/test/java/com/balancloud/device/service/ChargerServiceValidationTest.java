package com.balancloud.device.service;

import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.domain.Station;
import com.balancloud.device.dto.ChargerRequest;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.ocpp.OcppProtocol;
import com.balancloud.device.protocol.DeviceProtocolAdapter;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.protocol.ProtocolCapabilities;
import com.balancloud.device.protocol.RawFrame;
import com.balancloud.device.protocol.PlatformCommand;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.vo.ChargerVo;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.metric.TdengineClient;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.order.flow.AcCarOrderFlow;
import com.balancloud.order.flow.DcCarOrderFlow;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.flow.TwoWheelOrderFlow;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.support.TableInfoSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 建档组合校验：设备类型是端口数工厂，场景是业务时序，启动方式是设备属性，三者必须自洽。
 *
 * <p>测的是 {@code create} 这条唯一的建档入口，端口数通过实际插入的端口行断言。
 */
class ChargerServiceValidationTest {

    private static final long STATION_ID = 1L;
    private static final long CHARGER_ID = 10L;

    private final Map<Long, Charger> savedChargers = new HashMap<>();
    private final Map<Long, List<ChargerPort>> savedPorts = new HashMap<>();

    private ChargerService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(Charger.class, ChargerPort.class);
    }

    @BeforeEach
    void setUp() {
        savedChargers.clear();
        savedPorts.clear();

        ChargerMapper chargerMapper = mock(ChargerMapper.class);
        ChargerPortMapper portMapper = mock(ChargerPortMapper.class);
        StationMapper stationMapper = mock(StationMapper.class);
        StationScopeService scopeService = mock(StationScopeService.class);
        DeviceShadowService shadowService = mock(DeviceShadowService.class);
        TdengineClient tdengineClient = mock(TdengineClient.class);
        ChargeOrderMapper orderMapper = mock(ChargeOrderMapper.class);
        AppUserMapper appUserMapper = mock(AppUserMapper.class);
        AppVehicleMapper vehicleMapper = mock(AppVehicleMapper.class);
        SensitiveDataMasker masker = mock(SensitiveDataMasker.class);

        service = new ChargerService(chargerMapper, portMapper, stationMapper, scopeService, shadowService,
                tdengineClient, orderMapper, appUserMapper, vehicleMapper, masker,
                new OrderFlowRegistry(List.of(new TwoWheelOrderFlow(), new DcCarOrderFlow(), new AcCarOrderFlow())),
                new ProtocolAdapterRegistry(List.of(adapter("BALAN_MQTT"), adapter(OcppProtocol.PROTOCOL))));

        Station station = new Station();
        station.setId(STATION_ID);
        station.setName("测试场站");
        station.setStatus("ACTIVE");
        when(stationMapper.selectById(STATION_ID)).thenReturn(station);
        when(stationMapper.selectBatchIds(any())).thenReturn(List.of(station));
        when(chargerMapper.countByDeviceNoIncludingDeleted(any())).thenReturn(0L);
        doAnswer(invocation -> {
            Charger charger = invocation.getArgument(0);
            charger.setId(CHARGER_ID);
            savedChargers.put(CHARGER_ID, charger);
            return 1;
        }).when(chargerMapper).insert(any(Charger.class));
        when(chargerMapper.selectById(CHARGER_ID)).thenAnswer(invocation -> savedChargers.get(CHARGER_ID));
        doAnswer(invocation -> {
            ChargerPort port = invocation.getArgument(0);
            savedPorts.computeIfAbsent(port.getChargerId(), key -> new ArrayList<>()).add(port);
            return 1;
        }).when(portMapper).insert(any(ChargerPort.class));
        when(portMapper.selectByChargerId(anyLong()))
                .thenAnswer(invocation -> savedPorts.getOrDefault(invocation.getArgument(0), List.of()));
        when(shadowService.get(any())).thenReturn(Map.of());
    }

    @Test
    void acDualCreatesTwoIndependentPorts() {
        ChargerVo vo = service.create(request("AC_DUAL", OcppProtocol.PROTOCOL, AcCarOrderFlow.SCENE, null));

        assertThat(vo.getStartMethod()).isEqualTo(StartMethod.SCAN);
        List<ChargerPort> ports = savedPorts.get(CHARGER_ID);
        assertThat(ports).hasSize(2);
        assertThat(ports).extracting(ChargerPort::getPortNo).containsExactly(1, 2);
        assertThat(ports).allSatisfy(port -> assertThat(port.getStatus()).isEqualTo("IDLE"));
    }

    @Test
    void acSingleStoresPlugAndCharge() {
        ChargerVo vo = service.create(request("AC_SINGLE", OcppProtocol.PROTOCOL, AcCarOrderFlow.SCENE,
                StartMethod.PLUG_AND_CHARGE));

        assertThat(vo.getStartMethod()).isEqualTo(StartMethod.PLUG_AND_CHARGE);
        assertThat(vo.getScene()).isEqualTo(AcCarOrderFlow.SCENE);
        assertThat(savedPorts.get(CHARGER_ID)).hasSize(1);
    }

    @Test
    void startMethodDefaultsToScanWhenOmitted() {
        ChargerVo vo = service.create(request("AC_SINGLE", OcppProtocol.PROTOCOL, AcCarOrderFlow.SCENE, null));

        assertThat(vo.getStartMethod()).isEqualTo(StartMethod.SCAN);
    }

    @Test
    void acCarRejectsMqttProtocol() {
        assertThatThrownBy(() -> service.create(request("AC_SINGLE", "BALAN_MQTT", AcCarOrderFlow.SCENE, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只支持协议 " + OcppProtocol.PROTOCOL)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
    }

    @Test
    void acDeviceRejectsTwoWheelScene() {
        assertThatThrownBy(() -> service.create(request("AC_SINGLE", OcppProtocol.PROTOCOL, "TWOWHEEL", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能用于场景 " + AcCarOrderFlow.SCENE);
    }

    @Test
    void plugAndChargeIsLimitedToAcCar() {
        assertThatThrownBy(() -> service.create(request("DC_SINGLE", OcppProtocol.PROTOCOL, "DC_CAR",
                StartMethod.PLUG_AND_CHARGE)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("插枪即充只适用于交流桩场景 " + AcCarOrderFlow.SCENE);
    }

    @Test
    void unknownStartMethodIsRejected() {
        assertThatThrownBy(() -> service.create(request("AC_SINGLE", OcppProtocol.PROTOCOL, AcCarOrderFlow.SCENE,
                "RFID")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("启动方式不合法");
    }

    @Test
    void dcDualStillCreatesTwoPorts() {
        service.create(request("DC_DUAL", OcppProtocol.PROTOCOL, "DC_CAR", null));

        assertThat(savedPorts.get(CHARGER_ID)).hasSize(2);
    }

    private static ChargerRequest request(String deviceType, String protocol, String scene, String startMethod) {
        ChargerRequest request = new ChargerRequest();
        request.setStationId(STATION_ID);
        request.setDeviceNo("CP-TEST-001");
        request.setName("测试桩");
        request.setDeviceType(deviceType);
        request.setProtocol(protocol);
        request.setScene(scene);
        request.setStartMethod(startMethod);
        request.setIsSimulated(0);
        return request;
    }

    private static DeviceProtocolAdapter adapter(String protocol) {
        return new DeviceProtocolAdapter() {
            @Override
            public String protocol() {
                return protocol;
            }

            @Override
            public ProtocolCapabilities capabilities() {
                return new ProtocolCapabilities(true, true, true, true, false, true, false, 2,
                        ProtocolCapabilities.PortIdModel.PORT_NUMBER);
            }

            @Override
            public List<PlatformEvent> decode(RawFrame frame) {
                return List.of();
            }

            @Override
            public RawFrame encode(PlatformCommand command) {
                return null;
            }
        };
    }
}
