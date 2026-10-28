package com.balancloud.order.flow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFlowRegistryTest {

    @Test
    void indexesTheDefaultFlowAndTreatsBlankSceneAsTwoWheel() {
        OrderFlowRegistry registry = new OrderFlowRegistry(List.of(new TwoWheelOrderFlow()));

        assertThat(registry.available()).containsExactly("TWOWHEEL");
        assertThat(registry.require("TWOWHEEL")).isInstanceOf(TwoWheelOrderFlow.class);
        assertThat(registry.require("twowheel")).isInstanceOf(TwoWheelOrderFlow.class);
        assertThat(registry.require(null)).isInstanceOf(TwoWheelOrderFlow.class);
        assertThat(registry.require("  ")).isInstanceOf(TwoWheelOrderFlow.class);
    }

    @Test
    void unknownSceneIsReportedWithTheRegisteredOnes() {
        OrderFlowRegistry registry = new OrderFlowRegistry(List.of(new TwoWheelOrderFlow()));

        assertThat(registry.find("AC_CAR")).isEmpty();
        assertThatThrownBy(() -> registry.require("AC_CAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("场景流程未注册: AC_CAR")
                .hasMessageContaining("TWOWHEEL");
    }

    @Test
    void duplicateSceneFailsFastInsteadOfOverwriting() {
        assertThatThrownBy(() -> new OrderFlowRegistry(List.of(
                new TwoWheelOrderFlow(), new FakeFlow("TWOWHEEL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("场景流程重复注册: TWOWHEEL");
    }

    @Test
    void duplicateDetectionIsCaseInsensitive() {
        assertThatThrownBy(() -> new OrderFlowRegistry(List.of(
                new FakeFlow("twowheel"), new FakeFlow("TWOWHEEL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("场景流程重复注册: TWOWHEEL");
    }

    @Test
    void emptyRegistryStillWorksForFindButRequireExplains() {
        OrderFlowRegistry registry = new OrderFlowRegistry(List.of());

        assertThat(registry.available()).isEmpty();
        assertThat(registry.find("TWOWHEEL")).isEmpty();
        assertThatThrownBy(() -> registry.require("TWOWHEEL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("场景流程未注册");
    }

    private static final class FakeFlow implements OrderFlow {

        private final String scene;

        private FakeFlow(String scene) {
            this.scene = scene;
        }

        @Override
        public String scene() {
            return scene;
        }

        @Override
        public void assertStartable(StartContext ctx) {
        }

        @Override
        public List<PreChargePhase> preChargePhases() {
            return List.of();
        }

        @Override
        public boolean startsOnCommandAck() {
            return true;
        }

        @Override
        public OccupyDecision resolveOccupy(OccupyContext ctx) {
            return OccupyDecision.releaseToIdle();
        }

        @Override
        public List<AutoStopRule> autoStopRules(com.balancloud.order.model.RateSnapshot snapshot) {
            return List.of();
        }

        @Override
        public Duration stoppingTimeout() {
            return Duration.ofSeconds(15);
        }
    }
}
