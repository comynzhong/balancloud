package com.balancloud.device.vo;

import lombok.Data;

/** 跨租户反查出来的设备身份：OCPP 握手与上行归属判断都只认这个最小集合。 */
@Data
public class DeviceIdentityRef {

    private Long id;

    private Long tenantId;

    private Long stationId;

    private String deviceNo;

    private String protocol;

    private String scene;
}
