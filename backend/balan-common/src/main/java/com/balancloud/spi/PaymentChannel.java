package com.balancloud.spi;

import com.balancloud.spi.dto.PayRequest;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundRequest;
import com.balancloud.spi.dto.RefundResult;

/**
 * 支付通道扩展点：core 在支付与退款发起处调用，社区版为 mock 通道，标准版 jar 注册微信/支付宝实现。
 */
public interface PaymentChannel {

    /** 与 sys_config 的 payment.mode 取值对应，如 mock、wechat、alipay。 */
    String key();

    PayResult pay(PayRequest request);

    RefundResult refund(RefundRequest request);
}
