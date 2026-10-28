package com.balancloud.device.vo;

import lombok.Data;

@Data
public class ChargerGroupStats {

    private String groupName;
    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long chargingPortCount;
    private long faultPortCount;
}
