package com.balancloud.alert.model;

public enum AlertType {
    /** 端口温度超过设备 over_temp_c 阈值 */
    OVER_TEMP("过温", AlertLevel.CRITICAL, true),
    /** 端口电流超过设备 over_current_a 阈值 */
    OVER_CURRENT("过流", AlertLevel.CRITICAL, true),
    /** 电压高于额定电压上限 */
    OVER_VOLTAGE("过压", AlertLevel.MAJOR, true),
    /** 电压低于额定电压下限 */
    UNDER_VOLTAGE("欠压", AlertLevel.MAJOR, true),
    /** 端口功率超过额定功率上限 */
    OVERLOAD("过载", AlertLevel.MAJOR, true),
    /** 设备心跳丢失或主动下线 */
    OFFLINE("设备离线", AlertLevel.MAJOR, true),
    /** 设备上报或测点体现的端口故障 */
    PORT_FAULT("端口故障", AlertLevel.CRITICAL, false),
    /** 平台订单在充电中，设备上报端口空闲 */
    STATE_MISMATCH("状态不一致", AlertLevel.MAJOR, true);

    private final String label;
    private final AlertLevel defaultLevel;
    /** 是否允许平台自动恢复；端口故障必须人工确认 */
    private final boolean autoRecoverable;

    AlertType(String label, AlertLevel defaultLevel, boolean autoRecoverable) {
        this.label = label;
        this.defaultLevel = defaultLevel;
        this.autoRecoverable = autoRecoverable;
    }

    public String getLabel() {
        return label;
    }

    public AlertLevel getDefaultLevel() {
        return defaultLevel;
    }

    public boolean isAutoRecoverable() {
        return autoRecoverable;
    }

    public static AlertType parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AlertType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 厂商上报的类型不一定在平台枚举内，无法识别时按端口故障处理，原始类型放在告警内容里。
     */
    public static AlertType fromVendor(String value) {
        AlertType type = parse(value);
        return type == null ? PORT_FAULT : type;
    }
}
