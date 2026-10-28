package com.balancloud.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_manual_log")
public class OrderManualLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private String actionType;

    private String reason;

    private String beforeStatus;

    private String afterStatus;

    private BigDecimal beforeAmount;

    private BigDecimal afterAmount;

    private String operator;

    private LocalDateTime createTime;
}
