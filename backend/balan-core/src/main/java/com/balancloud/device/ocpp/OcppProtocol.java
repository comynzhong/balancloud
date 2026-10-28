package com.balancloud.device.ocpp;

/**
 * 协议常量集中在这里，避免握手、适配器、模拟器各写一份字符串。
 */
public final class OcppProtocol {

    public static final String PROTOCOL = "OCPP16J";

    public static final String SUBPROTOCOL = "ocpp1.6";

    private OcppProtocol() {
    }
}
