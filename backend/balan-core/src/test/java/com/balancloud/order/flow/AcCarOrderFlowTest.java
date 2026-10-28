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
 * 交流桩场景只承袭两轮与直流各自的一半：占位判定与两轮一致，启动/结束时序与直流一致。
 * 这里把五条差异钉住，避免以后被顺手改成别的场景。
 */
class AcCarOrderFlowTest {

    private final AcCarOrderFlow flow = new AcCarOrderFlow();

    @Test
    void declaresAcSceneWithOnlyThePlugPhase() {
        assertThat(flow.scene()).isEqualTo("AC_CAR");
        // 交流桩没有握手/BMS 协商，不编造阶段
        assertThat(flow.preChargePhases()).containsExactly(PreChargePhase.PLUGGED);
    }

    @Test
    void remoteAcksNeverAdvanceTheOrder() {
        assertThat(flow.startsOnCommandAck()).isFalse();
        assertThat(flow.settlesOnCommandAck()).isFalse();
    }

    @Test
    void stoppingTimeoutSitsBetweenTwoWheelAndDc() {
        assertThat(flow.stoppingTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(flow.stoppingTimeout())
                .isGreaterThan(new TwoWheelOrderFlow().stoppingTimeout())
                .isLessThan(new DcCarOrderFlow().stoppingTimeout());
    }

    @Test
    void acceptsIdleAndPluggedPreparingPorts() {
        flow.assertStartable(start("IDLE"));
        flow.assertStartable(start("OCCUPIED"));
    }

    @Test
    void refusesChargingOrFaultedPorts() {
        assertThatThrownBy(() -> flow.assertStartable(start("CHARGING")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("交流桩当前不是可启动状态（插枪后等待启动）")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> flow.assertStartable(start("FAULT")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("交流桩当前不是可启动状态（插枪后等待启动）");
    }

    @Test
    void occupancyFollowsTwoWheelBecauseAcGunsStayPlugged() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", true)))
                .isEqualTo(new TwoWheelOrderFlow().resolveOccupy(occupy(true, 9L, true, "IDLE", true)));
        // 已插枪且端口 IDLE：两轮口径是「仍占用」，交流桩同口径
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", true)).portStatus())
                .isEqualTo(OccupyDecision.OCCUPIED);
        // 未插枪：释放
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", false)))
                .isEqualTo(OccupyDecision.releaseToIdle());
    }

    @Test
    void autoStopStillPrefersAmountOverTime() {
        List<AutoStopRule> rules = flow.autoStopRules(null);
        assertThat(rules).extracting(AutoStopRule::name).containsExactly("amount-limit", "time-limit");

        ChargeOrder order = order("20.0000", 20, 100);
        assertThat(rules.get(0).evaluate(order, null))
                .get().extracting(AutoStopRule.Hit::reason).isEqualTo(StopReason.AMOUNT_LIMIT);

        order.setDurationSeconds(20 * 60);
        order.setTotalFee(new BigDecimal("1.0000"));
        assertThat(rules.get(1).evaluate(order, null))
                .get().extracting(AutoStopRule.Hit::reason).isEqualTo(StopReason.TIME_LIMIT);
    }

    private static StartContext start(String portStatus) {
        return new StartContext("CP-AC-001", "ONLINE", 1, portStatus, "1", null, false, null);
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
