package com.balancloud.device.domain;

/**
 * 设备的启动方式。扫码启动由平台下发 START 指令；插枪即充由桩直接发 StartTransaction，
 * 平台在设备事件里建单（见 §49）。
 */
public final class StartMethod {

    public static final String SCAN = "SCAN";

    public static final String PLUG_AND_CHARGE = "PLUG_AND_CHARGE";

    private StartMethod() {
    }

    public static boolean isPlugAndCharge(String value) {
        return PLUG_AND_CHARGE.equalsIgnoreCase(value == null ? null : value.trim());
    }
}
