package com.balancloud.app.vo;

import lombok.Data;

import java.util.List;

@Data
public class AppChargerVo {

    private Long id;
    private String deviceNo;
    private String name;
    private Integer portCount;
    private String onlineStatus;
    private String startMethod;
    private long idlePorts;
    private long chargingPorts;
    private long faultPorts;
    private List<AppPortVo> ports;
}
