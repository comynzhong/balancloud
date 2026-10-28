package com.balancloud.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_rate_detail")
public class OrderRateDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String periodType;

    private BigDecimal timePrice;

    private BigDecimal energyPrice;

    private BigDecimal servicePrice;

    private String serviceUnit;

    private Integer durationSeconds;

    private BigDecimal energyKwh;

    private BigDecimal timeFee;

    private BigDecimal energyFee;

    private BigDecimal serviceFee;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String delFlag;
}
