package com.balancloud.device.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StationVo {

    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal capacityKw;
    private String manager;
    private String phone;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private long deviceCount;
    private long onlineDeviceCount;
    private long portCount;
    private long chargingPortCount;
    private long faultPortCount;
}
