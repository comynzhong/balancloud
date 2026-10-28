package com.balancloud.app.vo;

import lombok.Data;

import java.util.List;

@Data
public class AppScanVo {

    private Long stationId;
    private String stationName;
    private String stationAddress;
    private Long chargerId;
    private String deviceNo;
    private String chargerName;
    private String onlineStatus;
    private String startMethod;
    private Integer portCount;
    private List<AppPortVo> ports;
    private AppRateVo rate;
}
