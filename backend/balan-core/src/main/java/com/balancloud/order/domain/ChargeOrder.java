package com.balancloud.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("charge_order")
public class ChargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long stationId;

    private Long chargerId;

    private Integer portNo;

    private Long appUserId;

    private Long vehicleId;

    private Long rateStrategyId;

    private String rateStrategyName;

    private String billingMode;

    private String rateSnapshotJson;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private BigDecimal startMeter;

    private BigDecimal endMeter;

    private BigDecimal energyKwh;

    private BigDecimal timeFee;

    private BigDecimal energyFee;

    private BigDecimal serviceFee;

    private Integer occupancySeconds;

    private BigDecimal occupancyFee;

    private BigDecimal totalFee;

    private Integer maxDurationMinutes;

    private BigDecimal chargeLimitAmount;

    private String status;

    private String paymentStatus;

    /** 下单时的启动方式快照：SCAN / PLUG_AND_CHARGE。 */
    private String startMethod;

    /** 即插即充匹配到的 VIN；扫码订单为空。 */
    private String vin;

    private String stopReason;

    private String stopMessage;

    private LocalDateTime paidAt;

    private String startCmdId;

    private String stopCmdId;

    private Long lastMetricTs;

    private BigDecimal lastMeter;

    private String lastPeriodType;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}
