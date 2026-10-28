package com.balancloud.spi.dto;

/**
 * 退款结果（§51.5.6）。真实通道可能异步处理，此时既不是成功也不是失败：
 * 调用方必须保持订单已支付，以退款回调为准，不能客户端猜测。
 */
public record RefundResult(boolean success, boolean pending, String channel,
                           String refundTradeNo, String message) {

    public static RefundResult ok(String channel, String refundTradeNo) {
        return new RefundResult(true, false, channel, refundTradeNo, "退款成功");
    }

    /** 通道已受理但尚未完成；订单保持 PAID，等待退款回调。 */
    public static RefundResult pending(String channel, String refundTradeNo) {
        return new RefundResult(false, true, channel, refundTradeNo, "退款处理中");
    }

    public static RefundResult failed(String channel, String message) {
        return new RefundResult(false, false, channel, null, message);
    }
}
