package com.balancloud.device.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChargerRequest {

    @NotNull(message = "归属场站不能为空")
    private Long stationId;

    /** 字符集与 OCPP handshake 的 identity 校验保持一致，MAC 形式的 CP 编号才能建档。 */
    @NotBlank(message = "设备编号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.:-]{3,64}$", message = "设备编号只能包含字母、数字、下划线、中划线、点号和冒号，长度 3-64")
    private String deviceNo;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    private String name;

    @Size(max = 64, message = "设备分组不能超过 64 个字符")
    private String groupName;

    @NotBlank(message = "设备类型不能为空")
    @Pattern(regexp = "SOCKET_2|SOCKET_10|SOCKET_12|DC_SINGLE|DC_DUAL|AC_SINGLE|AC_DUAL", message = "设备类型不合法")
    private String deviceType;

    @Size(max = 30, message = "固件版本不能超过 30 个字符")
    private String firmwareVersion;

    /** 必须命中已注册协议（启动期由 ProtocolAdapterRegistry 校验），校验放在 Service 里而不是注解。 */
    private String protocol;

    @Size(max = 32, message = "场景编码不能超过 32 个字符")
    private String scene;

    /** SCAN 扫码启动 / PLUG_AND_CHARGE 插枪即充；不传按 SCAN 处理。 */
    private String startMethod;

    @DecimalMin(value = "1", message = "额定电压必须大于 0")
    @Digits(integer = 6, fraction = 2, message = "额定电压格式不正确")
    private BigDecimal ratedVoltage;

    @DecimalMin(value = "0.001", message = "额定功率必须大于 0")
    @Digits(integer = 6, fraction = 3, message = "额定功率格式不正确")
    private BigDecimal ratedPowerKw;

    @DecimalMin(value = "1", message = "过温阈值必须大于 0")
    @Digits(integer = 4, fraction = 2, message = "过温阈值格式不正确")
    private BigDecimal overTempC;

    @DecimalMin(value = "0.001", message = "过流阈值必须大于 0")
    @Digits(integer = 4, fraction = 2, message = "过流阈值格式不正确")
    private BigDecimal overCurrentA;

    private Integer isSimulated;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
