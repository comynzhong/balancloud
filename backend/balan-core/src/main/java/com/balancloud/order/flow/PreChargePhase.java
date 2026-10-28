package com.balancloud.order.flow;

/**
 * 订单进入 CHARGING 之前要经过的阶段。两轮返回空列表，交流桩从 PLUGGED 起，直流桩要走到 PRE_CHARGE。
 *
 * <p>这些是<b>半状态</b>，只写设备影子，不写 {@code charger_port.status}：
 * 端口表的词汇表固定为 IDLE / CHARGING / OCCUPIED / FAULT 四个值（见 MVP 文档 §45.5 与 §46 Step 11 的断言）。
 */
public enum PreChargePhase {
    PLUGGED,
    AUTHENTICATING,
    HANDSHAKING,
    BMS_NEGOTIATING,
    PRE_CHARGE
}
