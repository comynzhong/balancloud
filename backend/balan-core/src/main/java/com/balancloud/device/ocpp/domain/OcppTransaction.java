package com.balancloud.device.ocpp.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCPP 1.6J 交易。主键直接作为下发给充电桩的 {@code transactionId}，
 * 平台订单号另存一列，两边都能反查。
 */
@Data
@TableName("ocpp_transaction")
public class OcppTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chargerId;

    private String deviceNo;

    private Integer connectorId;

    private String orderNo;

    private String idTag;

    /** Wh，OCPP 的原始单位。 */
    private Integer meterStart;

    private Integer meterStop;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    /** OCPP 原始 reason（如 EmergencyStop）；平台映射值在 charge_order.stop_reason。 */
    private String stopReason;

    /** ACTIVE / CLOSED。 */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
