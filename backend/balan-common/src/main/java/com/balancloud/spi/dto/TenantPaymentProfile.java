package com.balancloud.spi.dto;

/**
 * 租户收款配置（§51.5）。渠道适配器只读这份快照，不直接访问平台表。
 *
 * <p>密钥只带引用名（环境变量），真实值在调用通道前由实现方解析；本记录不得进入日志或前端。
 */
public record TenantPaymentProfile(Long tenantId,
                                   String channel,
                                   String mode,
                                   String merchantId,
                                   String appId,
                                   String subAppId,
                                   String apiV3KeyRef,
                                   String privateKeyRef,
                                   String serialNo,
                                   String notifyUrl) {

    public static final String MODE_SERVICE_PROVIDER = "SERVICE_PROVIDER";

    public static final String MODE_DIRECT = "DIRECT";

    /** 服务商模式：资金直接结算到该租户的特约商户，平台再分账收佣。 */
    public boolean serviceProvider() {
        return MODE_SERVICE_PROVIDER.equalsIgnoreCase(mode);
    }

    /** 直连模式：资金进租户自己的商户号，平台不参与资金流。 */
    public boolean direct() {
        return MODE_DIRECT.equalsIgnoreCase(mode);
    }
}
