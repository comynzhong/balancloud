package com.balancloud.device.dto;

import lombok.Data;

/**
 * 后台内置 OCPP 模拟器的启动参数。全部是离散值，不接受自由文本命令行。
 */
@Data
public class OcppSimulatorRequest {

    /** 必须是当前租户已建档、protocol=OCPP16J 的 device_no。 */
    private String identity;

    private Integer connectors;

    private Double powerKw;

    private Integer meterStepWh;

    private Integer intervalSeconds;

    /** 单次会话最长充电秒数，0 表示只等 RemoteStop。 */
    private Integer stopAfterSeconds;

    /** none / faulted / disconnect。 */
    private String fault;

    /** 即插即充用的 VIN；不传时取租户内第一辆填了 VIN 的车，仍找不到就不传。 */
    private String vin;
}
