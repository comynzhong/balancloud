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

class TwoWheelOrderFlowTest {

    private final TwoWheelOrderFlow flow = new TwoWheelOrderFlow();

    @Test
    void declaresTheDefaultSceneAndTwoWheelTiming() {
        assertThat(flow.scene()).isEqualTo("TWOWHEEL");
        assertThat(flow.preChargePhases()).isEmpty();
        assertThat(flow.startsOnCommandAck()).isTrue();
        assertThat(flow.stoppingTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void startUsesTheSamePortVocabularyAsBefore() {
        flow.assertStartable(start("IDLE"));
        flow.assertStartable(start("OCCUPIED"));

        assertThatThrownBy(() -> flow.assertStartable(start("CHARGING")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("端口当前不是空闲状态，无法启动充电")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> flow.assertStartable(start("FAULT")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("端口当前不是空闲状态，无法启动充电");
        assertThatThrownBy(() -> flow.assertStartable(start(null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("端口当前不是空闲状态，无法启动充电");
    }

    @Test
    void occupiesOnlyWhenEnabledAndPluggedAndOwnedByAnAppUser() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", "1")))
                .isEqualTo(OccupyDecision.keepOccupied());
    }

    @Test
    void doesNotOccupyWhenOccupancyIsDisabled() {
        assertThat(flow.resolveOccupy(occupy(false, 9L, true, "IDLE", "1")))
                .isEqualTo(OccupyDecision.releaseToIdle());
    }

    @Test
    void doesNotOccupyOperatorOrdersWithoutAnAppUser() {
        assertThat(flow.resolveOccupy(occupy(true, null, true, "IDLE", "1")))
                .isEqualTo(OccupyDecision.releaseToIdle());
    }

    @Test
    void doesNotOccupyWhenPortIsGoneOrPlugStateIsUnknown() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, false, "IDLE", "1")))
                .isEqualTo(OccupyDecision.releaseToIdle());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", null)))
                .isEqualTo(OccupyDecision.releaseToIdle());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "IDLE", "0")))
                .isEqualTo(OccupyDecision.releaseToIdle());
    }

    @Test
    void keepsFaultStatusEvenWhenTheGunIsStillPlugged() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "FAULT", "1")))
                .isEqualTo(OccupyDecision.fault());
    }

    @Test
    void releasesAnOccupiedPortAndSettlesOnlyAfterUnplug() {
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "OCCUPIED", "1")))
                .isEqualTo(OccupyDecision.keepOccupied());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "OCCUPIED", "0")))
                .isEqualTo(OccupyDecision.releaseToIdleAfterSettle());
        assertThat(flow.resolveOccupy(occupy(true, 9L, true, "OCCUPIED", null)))
                .isEqualTo(OccupyDecision.releaseToIdleAfterSettle());
    }

    @Test
    void autoStopKeepsTheAmountBeforeTimeOrder() {
        List<AutoStopRule> rules = flow.autoStopRules(null);
        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(AutoStopRule::name)
                .containsExactly("amount-limit", "time-limit");

        ChargeOrder order = order("10.0000", 20, 5);
        assertThat(rules.get(0).evaluate(order, null))
                .hasValueSatisfying(hit -> {
                    assertThat(hit.reason()).isEqualTo(StopReason.AMOUNT_LIMIT);
                    assertThat(hit.detail()).isEqualTo("达到金额上限");
                });
        assertThat(rules.get(1).evaluate(order, null)).isEmpty();

        ChargeOrder both = order("20.0000", 20, 100);
        assertThat(rules.get(0).evaluate(both, null)).get()
                .extracting(AutoStopRule.Hit::reason).isEqualTo(StopReason.AMOUNT_LIMIT);
    }

    @Test
    void autoStopTimesOutAtTheConfiguredMinutes() {
        List<AutoStopRule> rules = flow.autoStopRules(null);
        ChargeOrder order = new ChargeOrder();
        order.setTotalFee(new BigDecimal("1.0000"));
        order.setChargeLimitAmount(new BigDecimal("20.0000"));
        order.setMaxDurationMinutes(20);
        order.setDurationSeconds(20 * 60 - 1);
        assertThat(rules.get(0).evaluate(order, null)).isEmpty();
        assertThat(rules.get(1).evaluate(order, null)).isEmpty();

        order.setDurationSeconds(20 * 60);
        assertThat(rules.get(1).evaluate(order, null))
                .hasValueSatisfying(hit -> {
                    assertThat(hit.reason()).isEqualTo(StopReason.TIME_LIMIT);
                    assertThat(hit.detail()).isEqualTo("达到最大充电时长");
                });
    }

    private static StartContext start(String portStatus) {
        return new StartContext("BC-S12-001", "ONLINE", 1, portStatus, "0", null, false, null);
    }

    private static OccupyContext occupy(boolean enabled, Long appUserId, boolean portExists,
                                        String portStatus, String plugged) {
        return new OccupyContext(enabled, appUserId, portExists, portStatus,
                plugged == null ? null : "1".equals(plugged));
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
