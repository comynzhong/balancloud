package com.balancloud.spi.defaults;

import com.balancloud.spi.PaymentChannel;
import com.balancloud.spi.dto.PayRequest;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundRequest;
import com.balancloud.spi.dto.RefundResult;

/**
 * 社区版支付通道：不做真实资金流转，支付与退款一律同步成功。
 */
public class MockPaymentChannel implements PaymentChannel {

    @Override
    public String key() {
        return "mock";
    }

    @Override
    public PayResult pay(PayRequest request) {
        return PayResult.ok(key(), "MOCK" + request.orderNo());
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return RefundResult.ok(key(), "MOCKR" + request.refundNo());
    }
}
