package com.balancloud.order.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.spi.PaymentChannel;
import com.balancloud.spi.TenantPaymentProfileProvider;
import com.balancloud.spi.dto.PayRequest;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundRequest;
import com.balancloud.spi.dto.RefundResult;
import com.balancloud.system.service.SystemConfigService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * 支付通道选择：按 sys_config 的 payment.mode 找到对应 SPI 实现。
 * 社区版只有 mock 通道；模式配成 wechat/alipay 而 jar 未提供实现时，行为与拆分前的 assertMockPayment 一致。
 *
 * <p>真实通道按租户收款配置路由（§51.5.2）：mock 不受影响，非 mock 必须有启用的租户配置，
 * 取不到直接拒绝，绝不回落到平台全局商户号（那会变成二清）。
 */
@Service
public class PaymentGateway {

    private static final String MOCK = "mock";

    private final List<PaymentChannel> channels;
    private final SystemConfigService configService;
    private final TenantPaymentProfileProvider profileProvider;

    public PaymentGateway(List<PaymentChannel> channels,
                          SystemConfigService configService,
                          TenantPaymentProfileProvider profileProvider) {
        this.channels = channels;
        this.configService = configService;
        this.profileProvider = profileProvider;
    }

    public PaymentChannel current() {
        String mode = configService.getPaymentMode();
        return channels.stream()
                .filter(channel -> channel.key().equalsIgnoreCase(mode))
                .findFirst()
                .orElseThrow(() -> new ServiceException(501, "支付模式为「" + mode + "」，真实支付通道尚未接入"));
    }

    /** 是否仍在 mock 通道：后台代付与钱包充值这类旧入口据此拒绝（§51.5.8）。 */
    public boolean realChannel() {
        return !MOCK.equalsIgnoreCase(configService.getPaymentMode());
    }

    public PayResult pay(ChargeOrder order, String operator) {
        PayRequest request = new PayRequest(order.getId(), order.getOrderNo(), TenantContext.get(),
                order.getStationId(), order.getAppUserId(), nz(order.getTotalFee()),
                "充电订单 " + order.getOrderNo(), operator);
        return pay(request);
    }

    /**
     * 订单支付与钱包充值共用入口：支付总开关在这里统一拦，退款不走这里。
     */
    public PayResult pay(PayRequest request) {
        if (!configService.isPaymentEnabled()) {
            throw new ServiceException(400, "支付功能已关闭，请联系平台运营");
        }
        PaymentChannel channel = current();
        requireTenantProfile(request.tenantId(), channel);
        return channel.pay(request);
    }

    public RefundResult refund(ChargeOrder order, String refundNo, String reason, String operator) {
        RefundRequest request = new RefundRequest(order.getId(), order.getOrderNo(), refundNo,
                TenantContext.get(), order.getStationId(), order.getAppUserId(),
                nz(order.getTotalFee()), reason, operator);
        PaymentChannel channel = current();
        requireTenantProfile(request.tenantId(), channel);
        return channel.refund(request);
    }

    public List<String> channelKeys() {
        return channels.stream().map(PaymentChannel::key).toList();
    }

    private void requireTenantProfile(Long tenantId, PaymentChannel channel) {
        if (MOCK.equalsIgnoreCase(channel.key())) {
            return;
        }
        Long scope = tenantId != null ? tenantId : TenantContext.get();
        String channelKey = channel.key().toUpperCase(Locale.ROOT);
        if (profileProvider.find(scope, channelKey) == null) {
            throw new ServiceException(400, "该运营商尚未开通收款，请联系平台运营");
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

