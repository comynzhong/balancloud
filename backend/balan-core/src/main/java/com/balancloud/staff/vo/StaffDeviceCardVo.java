package com.balancloud.staff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StaffDeviceCardVo {

    private Long chargerId;
    private String deviceNo;
    private String deviceName;
    private Long stationId;
    private String stationName;
    private String deviceType;
    private String scene;
    private String protocol;
    private String startMethod;
    private String groupName;
    private String onlineStatus;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime lastMetricAt;
    private BigDecimal ratedVoltage;
    private BigDecimal ratedPowerKw;
    /** 过温阈值（§60.1）：没有这条线，读到 68℃ 也无从判断是否越限——告警判的就是它。 */
    private BigDecimal overTempC;
    /** 过流阈值，同上。 */
    private BigDecimal overCurrentA;
    private String firmwareVersion;
    private Integer portCount;

    /** 按钮显隐由服务端按 perms 判定，前端不做角色假设（§50 D4）。 */
    private boolean canStop;
    private boolean canUnlock;

    private List<StaffPortVo> portList;
    private List<StaffAlertVo> alertList;
}
