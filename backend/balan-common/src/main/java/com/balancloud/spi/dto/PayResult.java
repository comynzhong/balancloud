package com.balancloud.spi.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付调用结果。{@code success} 只表示「通道调用成功」，不等于已收款：
 * {@code pending=true} 表示预支付已创建，最终是否支付以回调或主动查单为准（§51.5.3）。
 */
public record PayResult(boolean success,
                        boolean pending,
                        String channel,
                        String tradeNo,
                        String message,
                        Map<String, Object> payload) {

    public PayResult {
        payload = payload == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** 已确认收款（mock 同步成功）。 */
    public static PayResult ok(String channel, String tradeNo) {
        return new PayResult(true, false, channel, tradeNo, "支付成功", null);
    }

    /** 预支付已创建，等待客户端调起支付；{@code payload} 是调起参数，原样下发给客户端。 */
    public static PayResult pending(String channel, String tradeNo, Map<String, Object> payload) {
        return new PayResult(true, true, channel, tradeNo, "支付确认中", payload);
    }

    public static PayResult failed(String channel, String message) {
        return new PayResult(false, false, channel, null, message, null);
    }
}
