package com.balancloud.order.flow;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.model.StopReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 直流场景只改六处：准入、半状态、ack 语义、占位判定、自动停集合、兜底超时。
 * 这里逐条把「和两轮不一样」的地方钉住，避免以后被顺手改成两轮口径。
 */
class DcCarOrderFlowTest {

    private final DcCarOrderFlow flow = new DcCarOrderFlow();

    @Test
    void declaresDcSceneAndWalksTheFullHandshakeBeforeCharging() {
        assertThat(flow.scene()).isEqualTo("DC_CAR");
        assertThat(flow.preChargePhases()).containsExactly(
                PreChargePhase.PLUGGED,
                PreChargePhase.AUTHENTICATING,
                PreChargePhase.HANDSHAKING,
                PreChargePhase.BMS_NEGOTIATING,
                PreChargePhase.PRE_CHARGE);
    }

    @Test
    void remoteAcksDoNotAdvanceTheOrder() {
        // RemoteStart 的 Accepted 只代表桩受理；真正开始等 StartTransaction
        assertThat(flow.startsOnCommandAck()).isFalse();
        // RemoteStop 同理，电量以 StopTransaction.meterStop 为准
        assertThat(flow.settlesOnCommandAck()).isFalse();
    }

    @Test
    void stoppingTimeoutIsLongerThanTwoWheelBecauseDcShutdownIsSlower() {
        assertThat(flow.stoppingTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(flow.stoppingTimeout())
                .isGreaterThan(new TwoWheelOrderFlow().stoppingTimeout());
    }

    @Test
    void acceptsBothIdleAndPluggedPreparingPorts() {
        flow.assertStartable(start("IDLE"));
        // 直流桩正常顺序是「插枪 → Preparing(OCCUPIED) → 远程启动」
        flow.assertStartable(start("OCCUPIED"));
    }

    @Test
    void refusesChargingOrFaultedPorts() {
        assertThatThrownBy(() -> flow.assertStartable(start("CHARGING")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("直流桩当前不是可启动状态（插枪后等待启动）")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> flow.assertStartable(start("FAULT")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("直流桩当前不是可启动状态（插枪后等待启动）");
    }

    @Test
    void neverChargesAnOccupancyFeeEvenWhenTheGunIsStillPlugged() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", true)))
                .isEqualTo(OccupyDecision.releaseToIdle());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "OCCUPIED", true)))
                .isEqualTo(OccupyDecision.releaseToIdle());
    }

    @Test
    void missingPortReleasesAndFaultedPortReportsFault() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, false, "IDLE", true)))
                .isEqualTo(OccupyDecision.releaseToIdle());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "FAULT", true)))
                .isEqualTo(OccupyDecision.fault());
    }

    @Test
    void autoStopKeepsTheSameAmountThenTimeOrderAsTwoWheel() {
        List<AutoStopRule> rules = flow.autoStopRules(null);
        assertThat(rules).extracting(AutoStopRule::name)
                .containsExactly("amount-limit", "time-limit");

        ChargeOrder order = order("20.0000", 20, 100);
        assertThat(rules.get(0).evaluate(order, null))
                .get().extracting(AutoStopRule.Hit::reason).isEqualTo(StopReason.AMOUNT_LIMIT);

        order.setDurationSeconds(20 * 60);
        order.setTotalFee(new BigDecimal("1.0000"));
        assertThat(rules.get(1).evaluate(order, null))
                .get().extracting(AutoStopRule.Hit::reason).isEqualTo(StopReason.TIME_LIMIT);
    }

    private static StartContext start(String portStatus) {
        return new StartContext("CP-DC-001", "ONLINE", 1, portStatus, "1", null, false, null);
    }

    private static OccupyContext occupy(boolean enabled, Long appUserId, boolean portExists,
                                        String portStatus, boolean plugged) {
        return new OccupyContext(enabled, appUserId, portExists, portStatus, plugged);
    }

    private static ChargeOrder order(String limit, int maxMinutes, int durationSeconds) {
        ChargeOrder order = new ChargeOrder();
        order.setTotalFee(new BigDecimal(limit));
        order.setChargeLimitAmount(new BigDecimal(limit));
        order.setMaxDurationMinutes(maxMinutes);
        order.setDurationSeconds(durationSeconds);
        return order;
    }
}
