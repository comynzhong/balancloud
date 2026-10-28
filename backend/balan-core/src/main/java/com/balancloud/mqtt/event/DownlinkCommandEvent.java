package com.balancloud.mqtt.event;

public class DownlinkCommandEvent {

    private final String deviceNo;
    private final String payload;

    public DownlinkCommandEvent(String deviceNo, String payload) {
        this.deviceNo = deviceNo;
        this.payload = payload;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public String getPayload() {
        return payload;
    }
}
