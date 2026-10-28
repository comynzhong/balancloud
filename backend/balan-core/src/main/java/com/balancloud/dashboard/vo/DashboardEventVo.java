package com.balancloud.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DashboardEventVo {

    /** ALERT / ORDER；设备上下线与实时功率只通过 WebSocket 下发 */
    private String category;

    /** 告警类型或订单状态，前端按字典渲染 */
    private String code;

    private String level;

    private String status;

    private String refNo;

    private Long stationId;

    private String deviceNo;

    private Integer portNo;

    /** 告警内容或订单停止说明 */
    private String detail;

    private BigDecimal energyKwh;

    private BigDecimal totalFee;

    private LocalDateTime time;
}
