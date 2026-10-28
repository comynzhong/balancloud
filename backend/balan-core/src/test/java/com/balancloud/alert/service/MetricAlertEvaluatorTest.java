package com.balancloud.alert.service;

import com.balancloud.alert.model.AlertType;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.mqtt.model.PortMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MetricAlertEvaluatorTest {

    private AlertService alertService;
    private MetricAlertEvaluator evaluator;
    private Charger charger;
    private ChargerPort port;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        evaluator = new MetricAlertEvaluator(alertService);
        charger = new Charger();
        charger.setId(1L);
        charger.setDeviceNo("BC-SIM-001");
        charger.setRatedVoltage(new BigDecimal("220.00"));
        charger.setOverTempC(new BigDecimal("60.00"));
        charger.setOverCurrentA(new BigDecimal("10.00"));
        port = new ChargerPort();
        port.setChargerId(1L);
        port.setPortNo(3);
        port.setRatedPowerKw(new BigDecimal("2.20"));
    }

    @Test
    void raisesOverTempWithDeviceConfiguredThreshold() {
        evaluator.evaluate(charger, port, metric("CHARGING", 2.0, 220.0, 5.0, 77.03));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(alertService).raise(eq(charger), eq(3), eq(AlertType.OVER_TEMP), isNull(),
                content.capture(), eq(BigDecimal.valueOf(77.03)), eq(new BigDecimal("60.00")));
        assertThat(content.getValue()).isEqualTo("端口温度 77.03℃，超过阈值 60.00℃");
    }

    @Test
    void stableMetricRequestsAutoResolveInsteadOfRaising() {
        evaluator.evaluate(charger, port, metric("CHARGING", 2.0, 220.0, 5.0, 40.0));

        verify(alertService).autoResolveIfStable(charger, 3, AlertType.OVER_TEMP);
        verify(alertService, never()).raise(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void derivesVoltageUpperLimitFromRatedVoltage() {
        PortMetric metric = metric("CHARGING", 2.0, 250.0, 5.0, 40.0);

        evaluator.evaluate(charger, port, metric);

        verify(alertService).raise(eq(charger), eq(3), eq(AlertType.OVER_VOLTAGE), isNull(), any(),
                eq(BigDecimal.valueOf(250.0)), eq(new BigDecimal("242.0000")));
    }

    @Test
    void derivesVoltageLowerLimitFromRatedVoltage() {
        evaluator.evaluate(charger, port, metric("CHARGING", 2.0, 190.0, 5.0, 40.0));

        verify(alertService).raise(eq(charger), eq(3), eq(AlertType.UNDER_VOLTAGE), isNull(), any(),
                eq(BigDecimal.valueOf(190.0)), eq(new BigDecimal("198.0000")));
    }

    @Test
    void skipsVoltageCheckWhenVoltageNotReported() {
        evaluator.evaluate(charger, port, metric("CHARGING", 2.0, 0.0, 5.0, 40.0));

        verify(alertService, never()).raise(any(), any(), eq(AlertType.OVER_VOLTAGE), any(), any(), any(), any());
        verify(alertService, never()).raise(any(), any(), eq(AlertType.UNDER_VOLTAGE), any(), any(), any(), any());
    }

    @Test
    void derivesOverloadLimitFromPortRatedPower() {
        evaluator.evaluate(charger, port, metric("CHARGING", 3.0, 220.0, 5.0, 40.0));

        verify(alertService).raise(eq(charger), eq(3), eq(AlertType.OVERLOAD), isNull(), any(),
                eq(BigDecimal.valueOf(3.0)), eq(new BigDecimal("2.4200")));
    }

    @Test
    void missingThresholdMeansNoJudgementForThatMetric() {
        charger.setOverTempC(null);
        charger.setOverCurrentA(null);

        evaluator.evaluate(charger, port, metric("CHARGING", 2.0, 220.0, 500.0, 90.0));

        verify(alertService, never()).raise(any(), any(), eq(AlertType.OVER_TEMP), any(), any(), any(), any());
        verify(alertService, never()).raise(any(), any(), eq(AlertType.OVER_CURRENT), any(), any(), any(), any());
        verify(alertService, never()).autoResolveIfStable(charger, 3, AlertType.OVER_TEMP);
        verify(alertService, never()).autoResolveIfStable(charger, 3, AlertType.OVER_CURRENT);
        verify(alertService).autoResolveIfStable(charger, 3, AlertType.OVERLOAD);
    }

    @Test
    void faultStatusRaisesPortFaultWithoutMetricValue() {
        evaluator.evaluate(charger, port, metric("FAULT", 0.0, 220.0, 0.0, 30.0));

        verify(alertService).raise(eq(charger), eq(3), eq(AlertType.PORT_FAULT), isNull(),
                eq("端口 3 上报故障状态"), isNull(), isNull());
    }

    @Test
    void ignoresIncompleteInput() {
        evaluator.evaluate(null, port, metric("CHARGING", 2.0, 220.0, 5.0, 90.0));
        evaluator.evaluate(charger, null, metric("CHARGING", 2.0, 220.0, 5.0, 90.0));
        evaluator.evaluate(charger, port, null);

        verifyNoInteractions(alertService);
    }

    private static PortMetric metric(String status, Double powerKw, Double voltage,
                                     Double currentA, Double temperature) {
        PortMetric metric = new PortMetric();
        metric.setPortNo(3);
        metric.setStatus(status);
        metric.setPowerKw(powerKw);
        metric.setVoltage(voltage);
        metric.setCurrentA(currentA);
        metric.setTemperature(temperature);
        metric.setEnergyTotal(10.0);
        return metric;
    }
}
