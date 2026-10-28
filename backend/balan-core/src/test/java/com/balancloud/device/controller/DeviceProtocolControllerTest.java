package com.balancloud.device.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.device.protocol.DeviceProtocolAdapter;
import com.balancloud.device.protocol.PlatformCommand;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.protocol.ProtocolCapabilities;
import com.balancloud.device.protocol.RawFrame;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.flow.TwoWheelOrderFlow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceProtocolControllerTest {

    private final DeviceProtocolController controller = new DeviceProtocolController(
            new ProtocolAdapterRegistry(List.of(new FakeAdapter())),
            new OrderFlowRegistry(List.of(new TwoWheelOrderFlow())));

    @Test
    @SuppressWarnings("unchecked")
    void protocolsExposeTheCapabilitiesOfEveryRegisteredDialect() {
        AjaxResult result = controller.protocols();

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get(AjaxResult.DATA_TAG);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("protocol", "TEST_PROTOCOL")
                .containsEntry("remoteStart", true)
                .containsEntry("remoteStop", false)
                .containsEntry("bmsHandshake", true)
                .containsEntry("maxPorts", 2)
                .containsEntry("portIdModel", "GUN_NUMBER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void flowsExposeTheSceneTimingAndRules() {
        AjaxResult result = controller.flows();

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get(AjaxResult.DATA_TAG);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("scene", "TWOWHEEL")
                .containsEntry("startsOnCommandAck", true)
                .containsEntry("stoppingTimeoutSeconds", 15L)
                .containsEntry("preChargePhases", List.of())
                .containsEntry("autoStopRules", List.of("amount-limit", "time-limit"));
    }

    private static final class FakeAdapter implements DeviceProtocolAdapter {

        @Override
        public String protocol() {
            return "TEST_PROTOCOL";
        }

        @Override
        public ProtocolCapabilities capabilities() {
            return new ProtocolCapabilities(true, false, false, false, false,
                    true, true, 2, ProtocolCapabilities.PortIdModel.GUN_NUMBER);
        }

        @Override
        public List<PlatformEvent> decode(RawFrame frame) {
            return List.of();
        }

        @Override
        public RawFrame encode(PlatformCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
