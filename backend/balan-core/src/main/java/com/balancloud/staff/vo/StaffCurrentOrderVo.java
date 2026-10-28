package com.balancloud.staff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StaffCurrentOrderVo {

    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private String orderStartMethod;
    private LocalDateTime orderStartTime;
    private Integer durationSeconds;
    private BigDecimal energyKwh;
    private BigDecimal totalAmount;
    private Integer maxDurationMinutes;
    private BigDecimal chargeLimitAmount;
    private String vinCode;
    private Long appUserId;
    private String appUserNickname;
    /** 按 security.mask.* 输出，不为运维开明文特权（§50 R3）。 */
    private String appUserPhone;
    private String vehiclePlate;
}
