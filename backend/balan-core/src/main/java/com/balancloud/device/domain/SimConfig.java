package com.balancloud.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sim_config")
public class SimConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Integer enabled;

    private Integer intervalSeconds;

    private BigDecimal faultProbability;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
