package com.balancloud.app.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_vehicle")
public class AppVehicle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appUserId;

    private String plateNo;

    /** 车辆识别代号，即插即充按它匹配车主；选填，填了则租户内唯一。 */
    private String vin;

    private String brand;

    private String model;

    private String color;

    private String isDefault;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
