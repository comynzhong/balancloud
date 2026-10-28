package com.balancloud.order.flow;

/**
 * 「这一单结束后，端口该落到哪个状态」所需的事实。
 *
 * <p>两轮看的是协议里的 {@code plugged}；交流桩要看是否有车停在车位上（地锁/摄像头/超时），
 * 大型车还要看枪是否归位——差异全在 flow 内部，调用方只传事实。
 *
 * @param plugged 报文直给的插枪信号，设备没说就是 null（null 不等于「没插枪」）
 */
public record OccupyContext(boolean occupancyEnabled,
                           Long appUserId,
                           boolean portExists,
                           String portStatus,
                           Boolean plugged) {

    public boolean pluggedIn() {
        return Boolean.TRUE.equals(plugged);
    }
}
