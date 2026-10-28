package com.balancloud.spi.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付结果语义（§51.5.3）：success 只表示通道调用成功，pending 才表示「还没收到钱」。
 */
class PayResultTest {

    @Test
    void okMeansConfirmedPayment() {
        PayResult result = PayResult.ok("mock", "MOCK1");

        assertTrue(result.success());
        assertFalse(result.pending());
        assertEquals("支付成功", result.message());
        assertNull(result.payload());
    }

    @Test
    void pendingKeepsPrepayPayload() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("timeStamp", "1730000000");
        params.put("paySign", "SIGN");

        PayResult result = PayResult.pending("wechat", "WX1", params);

        assertTrue(result.success());
        assertTrue(result.pending());
        assertEquals("WX1", result.tradeNo());
        assertEquals("1730000000", result.payload().get("timeStamp"));
        assertEquals("SIGN", result.payload().get("paySign"));
    }

    @Test
    void pendingPayloadIsACopyAndReadOnly() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nonceStr", "abc");

        PayResult result = PayResult.pending("wechat", "WX1", params);
        params.put("nonceStr", "changed");

        assertEquals("abc", result.payload().get("nonceStr"));
        assertThrows(UnsupportedOperationException.class, () -> result.payload().put("x", "y"));
    }

    @Test
    void failedNeverCarriesTradeNo() {
        PayResult result = PayResult.failed("wechat", "商户号未配置");

        assertFalse(result.success());
        assertFalse(result.pending());
        assertNull(result.tradeNo());
        assertEquals("商户号未配置", result.message());
    }
}
