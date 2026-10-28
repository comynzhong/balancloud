package com.balancloud.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChargingActionVo {

    private Long orderId;

    private String orderNo;

    private Integer portNo;

    private String status;

    private String cmdId;
}
