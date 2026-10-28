package com.balancloud.device.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 仿真器重锚阈值：正常上报的滞后不能触发，实例接手时的过期影子必须触发。 */
class SimulatorMeterReanchorTest {

    @Test
    void oneIntervalOfNormalAccumulationIsNotStale() {
        // 2.2 kW × 60 s = 0.037 kWh，台账最多落后一个上报间隔
        assertThat(SimulatorService.meterIsStale(24.9146, 24.8775, 2.2D)).isFalse();
        assertThat(SimulatorService.meterIsStale(24.8775, 24.9146, 2.2D)).isFalse();
    }

    @Test
    void aShadowLeftBehindByAnotherLeaderIsStale() {
        // 双实例实测：过期影子 442.06 对台账 24.88，一帧就把订单炸成 417 度
        assertThat(SimulatorService.meterIsStale(442.0593, 24.8779, 2.2D)).isTrue();
        assertThat(SimulatorService.meterIsStale(24.8779, 442.0593, 2.2D)).isTrue();
    }

    @Test
    void theToleranceScalesWithRatedPower() {
        // 120 kW 直流桩一个 60 s 间隔就是 2 kWh，不能按 2.2 kW 的尺子判它过期
        assertThat(SimulatorService.meterIsStale(122.0D, 120.0D, 120D)).isFalse();
        assertThat(SimulatorService.meterIsStale(400.0D, 120.0D, 120D)).isTrue();
    }

    @Test
    void anUnknownRatedPowerStillLeavesAPlainMargin() {
        assertThat(SimulatorService.meterIsStale(1.6D, 1.0D, 0D)).isFalse();
        assertThat(SimulatorService.meterIsStale(9.0D, 1.0D, 0D)).isTrue();
    }
}
