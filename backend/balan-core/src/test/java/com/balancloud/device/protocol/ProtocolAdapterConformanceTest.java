package com.balancloud.device.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任何 {@link DeviceProtocolAdapter} 都必须过的五条一致性断言（§46 Step 3）。
 *
 * <p>第三方只要继承本类、提供自己协议的四段夹具，就能自证接得对；不需要读 core 的业务代码。
 * 这五条挑的是最容易出错、也最难在联调时看出来的地方：坏帧的处理方式、cmdId 能不能对上、
 * 能力声明是不是真的、幂等键稳不稳、解不出来时会不会留下半个状态。
 *
 * <p>分发方式（同模块 test 目录还是 test-jar）见 MVP 文档 §46 Step 3 与 docs/PROTOCOL-SDK.md 第 6 节。
 */
public abstract class ProtocolAdapterConformanceTest {

    protected abstract DeviceProtocolAdapter adapter();

    /** 本协议的一条合法测点帧，至少一个端口。 */
    protected abstract RawFrame metricFrame();

    /** 设备对 cmdId 的回执帧；必须与 encode 出去的帧里的 cmdId 对得上。 */
    protected abstract RawFrame ackFrame(String cmdId, boolean success);

    /** 本协议支持的一条启动指令。 */
    protected abstract PlatformCommand startCommand(String cmdId);

    /** 主题正确但正文是垃圾的一帧。 */
    protected abstract RawFrame malformedMetricFrame();

    @Test
    void malformedFrameDecodesToNothingInsteadOfThrowing() {
        // 一条坏报文不能把整条上行链路带停，所以这里既不许抛异常、也不许产出半截事件
        List<PlatformEvent> events = adapter().decode(malformedMetricFrame());

        assertThat(events).isEmpty();
    }

    @Test
    void encodedCmdIdRoundTripsThroughTheDeviceAck() {
        String cmdId = "CONF-CMD-0001";
        RawFrame downlink = adapter().encode(startCommand(cmdId));

        assertThat(downlink.topicOrPeer()).isNotBlank();
        assertThat(downlink.body()).isNotEmpty();
        assertThat(downlink.bodyAsText()).contains(cmdId);

        PlatformEvent ack = adapter().decode(ackFrame(cmdId, true)).get(0);
        assertThat(ack).isInstanceOf(PlatformEvent.CommandAck.class);
        assertThat(((PlatformEvent.CommandAck) ack).cmdId()).isEqualTo(cmdId);
        assertThat(((PlatformEvent.CommandAck) ack).success()).isTrue();
    }

    @Test
    void capabilitiesMatchWhatTheAdapterActuallyDoes() {
        ProtocolCapabilities capabilities = adapter().capabilities();
        assertThat(capabilities.maxPorts()).isPositive();
        assertThat(capabilities.portIdModel()).isNotNull();

        if (capabilities.remoteStart()) {
            assertThat(adapter().encode(startCommand("CONF-CAP-1")).body()).isNotEmpty();
        }
        if (capabilities.perPortMetering()) {
            PlatformEvent event = adapter().decode(metricFrame()).get(0);
            assertThat(event).isInstanceOf(PlatformEvent.MetricReport.class);
            assertThat(((PlatformEvent.MetricReport) event).ports()).isNotEmpty();
        }
        if (capabilities.ota()) {
            assertThat(adapter().encode(PlatformCommand.ota("CONF-DEV", java.util.Map.of("version", "1.0.0")))
                    .body()).isNotEmpty();
        }
    }

    @Test
    void dedupKeyIsStableForRedeliveredFrames() {
        // QoS1 重投与订阅组重分配都靠这个键；同帧两次调用必须给同一个答案
        Optional<String> first = adapter().dedupKey(metricFrame());
        Optional<String> second = adapter().dedupKey(metricFrame());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void unrecognizedIsPureAndCarriesAReason() {
        RawFrame frame = RawFrame.mqtt(adapterMetricTopicPrefix() + "THIS_EVENT_DOES_NOT_EXIST", "{}");

        List<PlatformEvent> events = adapter().decode(frame);

        assertThat(events).hasSize(1);
        PlatformEvent.Unrecognized unrecognized = (PlatformEvent.Unrecognized) events.get(0);
        assertThat(unrecognized.reason()).isNotBlank();
        // 同一个坏帧再来一次，答案不变：适配器不许有状态
        assertThat(adapter().decode(frame)).isEqualTo(events);
    }

    /** 本协议的上行主题前缀，以 / 结尾；坏帧夹具用它拼出一个平台没见过的事件名。 */
    protected abstract String adapterMetricTopicPrefix();
}
