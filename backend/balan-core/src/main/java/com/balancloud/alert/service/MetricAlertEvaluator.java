package com.balancloud.alert.service;

import com.balancloud.alert.model.AlertType;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.mqtt.model.PortMetric;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 消费测点做阈值判断：过温、过流、过压、欠压、过载、端口故障。
 * 电压/功率阈值由额定值推导（额定电压 ±10%、额定功率 110%），温度与电流取设备可配置阈值。
 */
@Service
public class MetricAlertEvaluator {

    private static final BigDecimal VOLTAGE_HIGH_RATIO = new BigDecimal("1.10");
    private static final BigDecimal VOLTAGE_LOW_RATIO = new BigDecimal("0.90");
    private static final BigDecimal POWER_HIGH_RATIO = new BigDecimal("1.10");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AlertService alertService;

    public MetricAlertEvaluator(AlertService alertService) {
        this.alertService = alertService;
    }

    public void evaluate(Charger charger, ChargerPort port, PortMetric metric) {
        if (charger == null || port == null || metric == null) {
            return;
        }
        Integer portNo = port.getPortNo();
        checkUpper(charger, portNo, AlertType.OVER_TEMP, metric.getTemperature(), charger.getOverTempC(), "端口温度", "℃");
        checkUpper(charger, portNo, AlertType.OVER_CURRENT, metric.getCurrentA(), charger.getOverCurrentA(), "端口电流", "A");
        checkVoltage(charger, portNo, metric.getVoltage());
        checkUpper(charger, portNo, AlertType.OVERLOAD, metric.getPowerKw(),
                ratio(port.getRatedPowerKw(), POWER_HIGH_RATIO), "端口功率", "kW");
        if ("FAULT".equals(metric.getStatus())) {
            alertService.raise(charger, portNo, AlertType.PORT_FAULT, null,
                    String.format("端口 %d 上报故障状态", portNo), null, null);
        }
    }

    private void checkUpper(Charger charger, Integer portNo, AlertType type,
                            Double actual, BigDecimal limit, String label, String unit) {
        if (actual == null || limit == null || limit.compareTo(ZERO) <= 0) {
            return;
        }
        BigDecimal value = BigDecimal.valueOf(actual);
        if (value.compareTo(limit) > 0) {
            alertService.clearNormalStreak(charger.getId(), portNo, type);
            alertService.raise(charger, portNo, type, null,
                    String.format("%s %.2f%s，超过阈值 %.2f%s", label, actual, unit, limit, unit), value, limit);
            return;
        }
        alertService.autoResolveIfStable(charger, portNo, type);
    }

    private void checkVoltage(Charger charger, Integer portNo, Double voltage) {
        BigDecimal rated = charger.getRatedVoltage();
        if (voltage == null || voltage <= 0 || rated == null || rated.compareTo(ZERO) <= 0) {
            return;
        }
        BigDecimal value = BigDecimal.valueOf(voltage);
        BigDecimal high = ratio(rated, VOLTAGE_HIGH_RATIO);
        BigDecimal low = ratio(rated, VOLTAGE_LOW_RATIO);
        if (value.compareTo(high) > 0) {
            alertService.clearNormalStreak(charger.getId(), portNo, AlertType.OVER_VOLTAGE);
            alertService.raise(charger, portNo, AlertType.OVER_VOLTAGE, null,
                    String.format("端口电压 %.2fV，超过额定 %.2fV 上限 %.2fV", voltage, rated, high), value, high);
            return;
        }
        alertService.autoResolveIfStable(charger, portNo, AlertType.OVER_VOLTAGE);
        if (value.compareTo(low) < 0) {
            alertService.clearNormalStreak(charger.getId(), portNo, AlertType.UNDER_VOLTAGE);
            alertService.raise(charger, portNo, AlertType.UNDER_VOLTAGE, null,
                    String.format("端口电压 %.2fV，低于额定 %.2fV 下限 %.2fV", voltage, rated, low), value, low);
            return;
        }
        alertService.autoResolveIfStable(charger, portNo, AlertType.UNDER_VOLTAGE);
    }

    private BigDecimal ratio(BigDecimal base, BigDecimal factor) {
        if (base == null) {
            return null;
        }
        return base.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }
}
