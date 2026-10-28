package com.balancloud.device.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargerVo {

    private Long id;
    private Long stationId;
    private String stationName;
    private String deviceNo;
    private String name;
    private String groupName;
    private String deviceType;
    private Integer portCount;
    private String protocol;
    private String scene;
    private String startMethod;
    private String firmwareVersion;
    private BigDecimal ratedVoltage;
    private BigDecimal ratedPowerKw;
    private BigDecimal overTempC;
    private BigDecimal overCurrentA;
    private String onlineStatus;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime lastMetricAt;
    private Integer isSimulated;
    private String remark;
    private LocalDateTime createTime;

    private long chargingPorts;
    private long idlePorts;
    private long faultPorts;
    private double currentPowerKw;
    private double maxTemperature;
}
