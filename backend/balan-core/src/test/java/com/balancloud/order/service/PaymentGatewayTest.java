package com.balancloud.order.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.spi.PaymentChannel;
import com.balancloud.spi.TenantPaymentProfileProvider;
import com.balancloud.spi.dto.PayRequest;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundRequest;
import com.balancloud.spi.dto.RefundResult;
import com.balancloud.spi.dto.TenantPaymentProfile;
import com.balancloud.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通道路由（§51.5.2）：mock 不受租户配置影响；真实通道必须有启用的租户收款配置，
 * 取不到直接 400，不能回落到平台全局商户号。
 */
class PaymentGatewayTest {

    private final SystemConfigService configService = mock(SystemConfigService.class);

    private final TenantPaymentProfileProvider profiles = mock(TenantPaymentProfileProvider.class);

    private PaymentGateway gateway(PaymentChannel... channels) {
        return new PaymentGateway(List.of(channels), configService, profiles);
    }

    @Test
    void payIsRejectedWhenSwitchIsOff() {
        RecordingChannel channel = new RecordingChannel("mock");
        when(configService.isPaymentEnabled()).thenReturn(false);
        PaymentGateway gateway = gateway(channel);

        assertThatThrownBy(() -> gateway.pay(recharge()))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(((ServiceException) error).getCode()).isEqualTo(400))
                .hasMessageContaining("支付功能已关闭");
        assertThat(channel.payCalls).isZero();
    }

    @Test
    void mockModeIgnoresTenantProfile() {
        RecordingChannel channel = new RecordingChannel("mock");
        when(configService.isPaymentEnabled()).thenReturn(true);
        when(configService.getPaymentMode()).thenReturn("mock");
        PaymentGateway gateway = gateway(channel);

        PayResult result = gateway.pay(recharge());

        assertThat(result.success()).isTrue();
        assertThat(result.pending()).isFalse();
        assertThat(result.tradeNo()).isEqualTo("MOCKRC-TEST");
        assertThat(channel.payCalls).isEqualTo(1);
        verifyNoInteractions(profiles);
    }

    @Test
    void realModeRoutesToTenantProfile() {
        RecordingChannel channel = new RecordingChannel("wechat");
        when(configService.isPaymentEnabled()).thenReturn(true);
        when(configService.getPaymentMode()).thenReturn("wechat");
        when(profiles.find(7L, "WECHAT")).thenReturn(serviceProvider());
        PaymentGateway gateway = gateway(channel);

        PayResult result = gateway.pay(recharge());

        assertThat(result.channel()).isEqualTo("wechat");
        assertThat(channel.payCalls).isEqualTo(1);
    }

    @Test
    void realModeWithoutTenantProfileIsRejectedInsteadOfFallingBack() {
        RecordingChannel channel = new RecordingChannel("wechat");
        when(configService.isPaymentEnabled()).thenReturn(true);
        when(configService.getPaymentMode()).thenReturn("wechat");
        PaymentGateway gateway = gateway(channel);

        assertThatThrownBy(() -> gateway.pay(recharge()))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(((ServiceException) error).getCode()).isEqualTo(400))
                .hasMessageContaining("尚未开通收款");
        assertThat(channel.payCalls).isZero();
    }

    @Test
    void refundAlsoRequiresTenantProfile() {
        RecordingChannel channel = new RecordingChannel("wechat");
        when(configService.getPaymentMode()).thenReturn("wechat");
        PaymentGateway gateway = gateway(channel);
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo("BC2026");
        order.setStationId(2L);
        order.setAppUserId(3L);
        order.setTotalFee(new BigDecimal("18.00"));

        assertThatThrownBy(() -> gateway.refund(order, "RF001", "测试退款", "admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未开通收款");
        assertThat(channel.refundCalls).isZero();
    }

    @Test
    void unregisteredModeFailsInsteadOfPretending() {
        when(configService.isPaymentEnabled()).thenReturn(true);
        when(configService.getPaymentMode()).thenReturn("wechat");
        PaymentGateway gateway = gateway(new RecordingChannel("mock"));

        assertThatThrownBy(() -> gateway.pay(recharge()))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(((ServiceException) error).getCode()).isEqualTo(501))
                .hasMessageContaining("真实支付通道尚未接入");
    }

    @Test
    void realChannelFlagFollowsGlobalMode() {
        when(configService.getPaymentMode()).thenReturn("mock");
        assertThat(gateway(new RecordingChannel("mock")).realChannel()).isFalse();

        when(configService.getPaymentMode()).thenReturn("wechat");
        assertThat(gateway(new RecordingChannel("wechat")).realChannel()).isTrue();
    }

    private TenantPaymentProfile serviceProvider() {
        return new TenantPaymentProfile(7L, "WECHAT", TenantPaymentProfile.MODE_SERVICE_PROVIDER,
                "1900000109", null, null, "WECHAT_T12_API_V3_KEY", null, null, null);
    }

    private PayRequest recharge() {
        return new PayRequest(null, "RC-TEST", 7L, null, 7L,
                new BigDecimal("10.00"), "钱包充值", "test");
    }

    private static final class RecordingChannel implements PaymentChannel {

        private final String key;

        private int payCalls;

        private int refundCalls;

        private RecordingChannel(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public PayResult pay(PayRequest request) {
            payCalls++;
            return PayResult.ok(key, "MOCK" + request.orderNo());
        }

        @Override
        public RefundResult refund(RefundRequest request) {
            refundCalls++;
            return RefundResult.ok(key, "MOCKR" + request.refundNo());
        }
    }
}
