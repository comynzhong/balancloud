package com.balancloud.app.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppStationVo {

    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private String manager;
    private String status;

    /** 仅当请求带经纬度时回填，单位米；场站缺坐标或请求缺坐标时为 null。 */
    private Long distanceMeters;

    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long idlePortCount;
    private long chargingPortCount;
    private long faultPortCount;
}
