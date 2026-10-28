package com.balancloud.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderRefundVo {

    private Long id;
    private String refundNo;
    private BigDecimal amount;
    private String reason;
    private String status;
    private String operator;
    private LocalDateTime refundTime;
}
