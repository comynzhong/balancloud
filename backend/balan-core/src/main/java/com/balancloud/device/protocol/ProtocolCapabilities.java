package com.balancloud.device.protocol;

/**
 * 这台桩能做什么，由适配器回答。业务代码判断能力时读这里，不读 scene、不读 protocol 字符串。
 *
 * <p>同一个场景可以接不同厂商的桩，能力不一样；把场景当能力用是这套抽象最想避免的错。
 */
public record ProtocolCapabilities(boolean remoteStart, boolean remoteStop, boolean unlock,
                                   boolean setThreshold, boolean ota,
                                   boolean perPortMetering, boolean bmsHandshake,
                                   int maxPorts, PortIdModel portIdModel) {

    /** CONNECTOR_ID：OCPP 的 connectorId，从 1 开始，0 表示桩级。 */
    public enum PortIdModel { PORT_NUMBER, GUN_NUMBER, GUN_PLUS_PLUG, CONNECTOR_ID }
}
