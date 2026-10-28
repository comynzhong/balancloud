package com.balancloud.device.protocol;

import java.util.List;

/**
 * 一个端口的一次采样。前九个字段是现有 BALAN_MQTT 方言的全部信号，
 * 后面的车类字段一律可空——两轮插座解不出来就留 null，不允许适配器造默认值。
 */
public record PortSample(Integer portNo, String status, Double voltage, Double currentA,
                         Double temperature, Double powerKw, Double energyTotal,
                         Integer signalStrength, Boolean plugged,
                         Integer soc, Double bmsDemandVoltage, Double bmsDemandCurrent,
                         List<Double> gunTemperatures, Integer gunNo, Integer masterGunNo,
                         Long chargeSeconds, String stopReasonCode, String meterSerial) {

    /** 只带现有插座信号的构造，供 BALAN_MQTT 适配器逐条搬运时使用。 */
    public static PortSample of(Integer portNo, String status, Double voltage, Double currentA,
                                Double temperature, Double powerKw, Double energyTotal,
                                Integer signalStrength, Boolean plugged) {
        return new PortSample(portNo, status, voltage, currentA, temperature, powerKw, energyTotal,
                signalStrength, plugged, null, null, null, null, null, null, null, null, null);
    }
}
