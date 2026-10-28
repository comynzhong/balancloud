package com.balancloud.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("charger_port")
public class ChargerPort {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chargerId;

    private Integer portNo;

    private BigDecimal ratedCurrentA;

    private BigDecimal ratedPowerKw;

    private String status;

    private String currentOrderNo;

    private LocalDateTime lastMetricAt;

    private String plugged;

    private LocalDateTime occupiedSince;

    private String occupancyOrderNo;
}
