package com.balancloud.app.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AppUserAdminVo {

    private Long id;
    private String nickname;
    private String phone;
    private String avatar;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;

    private long orderCount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private LocalDateTime lastOrderTime;
    private int vehicleCount;

    private String activeOrderNo;
    private Long activeOrderId;
    private String activeStationName;
    private Integer activePortNo;
    private LocalDateTime activeStartTime;
    private Integer activeDurationSeconds;
}
