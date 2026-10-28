package com.balancloud.order.flow;

import com.balancloud.device.protocol.ProtocolCapabilities;

/**
 * 「这台桩的这个端口，现在能不能起一单」所需的全部事实。
 *
 * <p>刻意不含 scene：场景已经在上游选完 flow，进到 flow 内部只该看设备事实和 {@link ProtocolCapabilities}。
 *
 * @param onlineStatus {@code charger.online_status}，ONLINE / OFFLINE 等原样传入
 * @param plugged      {@code charger_port.plugged}，"1" 表示枪还插着；两轮与四轮语义相同
 * @param hasActiveOrder 该端口是否存在未结束订单
 */
public record StartContext(String deviceNo,
                           String onlineStatus,
                           Integer portNo,
                           String portStatus,
                           String plugged,
                           String currentOrderNo,
                           boolean hasActiveOrder,
                           ProtocolCapabilities capabilities) {

    public boolean online() {
        return "ONLINE".equals(onlineStatus);
    }

    public boolean idle() {
        return "IDLE".equals(portStatus);
    }

    public boolean occupied() {
        return "OCCUPIED".equals(portStatus);
    }
}
