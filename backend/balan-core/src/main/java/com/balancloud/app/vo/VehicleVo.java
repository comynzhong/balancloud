package com.balancloud.app.vo;

import com.balancloud.app.domain.AppVehicle;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleVo {

    private Long id;
    private Long appUserId;
    private String appUserNickname;
    private String appUserPhone;
    private String plateNo;
    private String vin;
    private String brand;
    private String model;
    private String color;
    private String isDefault;
    private LocalDateTime createTime;

    public static VehicleVo of(AppVehicle vehicle) {
        VehicleVo vo = new VehicleVo();
        vo.setId(vehicle.getId());
        vo.setAppUserId(vehicle.getAppUserId());
        vo.setPlateNo(vehicle.getPlateNo());
        vo.setVin(vehicle.getVin());
        vo.setBrand(vehicle.getBrand());
        vo.setModel(vehicle.getModel());
        vo.setColor(vehicle.getColor());
        vo.setIsDefault(vehicle.getIsDefault());
        vo.setCreateTime(vehicle.getCreateTime());
        return vo;
    }
}
