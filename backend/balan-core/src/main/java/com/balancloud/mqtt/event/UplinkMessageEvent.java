package com.balancloud.mqtt.event;

public class UplinkMessageEvent {

    private final String deviceNo;
    private final String event;
    private final String payload;
    private final Long tenantId;

    public UplinkMessageEvent(String deviceNo, String event, String payload, Long tenantId) {
        this.deviceNo = deviceNo;
        this.event = event;
        this.payload = payload;
        this.tenantId = tenantId;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public String getEvent() {
        return event;
    }

    public String getPayload() {
        return payload;
    }

    /** 报文归属租户；解析不出来时为 null，处理侧按「无法归属」丢弃。 */
    public Long getTenantId() {
        return tenantId;
    }
}
