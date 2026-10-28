package com.balancloud.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("charger")
public class Charger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private String deviceNo;

    private String name;

    private String groupName;

    private String deviceType;

    private Integer portCount;

    private String protocol;

    /** 业务时序场景（TWOWHEEL / AC_CAR / DC_CAR / HEAVY），与端口数工厂 deviceType 正交。 */
    private String scene;

    /** 启动方式：SCAN 扫码下单，PLUG_AND_CHARGE 插枪即充（由设备 StartTransaction 建单）。 */
    private String startMethod;

    private String firmwareVersion;

    private BigDecimal ratedVoltage;

    private BigDecimal ratedPowerKw;

    private BigDecimal overTempC;

    private BigDecimal overCurrentA;

    private String onlineStatus;

    private LocalDateTime lastOnlineAt;

    private LocalDateTime lastMetricAt;

    private Integer isSimulated;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private String delFlag;
}
