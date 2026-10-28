package com.balancloud.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_refund")
public class OrderRefund {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    private Long stationId;

    private Long appUserId;

    private BigDecimal amount;

    private String reason;

    private String status;

    private String operator;

    private LocalDateTime refundTime;

    private LocalDateTime createTime;
}
