package com.balancloud.device.vo;

import lombok.Data;

@Data
public class StationStats {

    private Long stationId;
    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long chargingPortCount;
    private long faultPortCount;
}
