package com.balancloud.spi.defaults;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.spi.NotificationDispatcher;
import com.balancloud.order.model.RateSnapshot;
import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.NotificationChannel;
import com.balancloud.spi.PaymentChannel;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.TenantConfigSource;
import com.balancloud.spi.TenantMenuScope;
import com.balancloud.spi.TenantPaymentProfileProvider;
import com.balancloud.spi.TenantRegistrationService;
import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.spi.dto.BenefitQuery;
import com.balancloud.spi.dto.NotificationEvent;
import com.balancloud.spi.dto.PayRequest;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundRequest;
import com.balancloud.spi.dto.RefundResult;
import com.balancloud.spi.dto.RegistrationApplication;
import com.balancloud.spi.dto.SettlementContext;
import com.balancloud.spi.dto.StartGuardQuery;
import com.balancloud.system.domain.SysMessage;
import com.balancloud.system.mapper.SysMessageMapper;
import com.balancloud.ws.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SPI 默认实现与「jar 覆盖」机制的契约测试：社区版拿到 9 个默认 bean，平台侧同类型 bean 能替换默认实现，
 * 额外的通知渠道与站内信共存。
 */
class SpiDefaultsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpiDefaultsAutoConfiguration.class))
            .withBean(SysMessageMapper.class, () -> mock(SysMessageMapper.class))
            .withBean(EventStreamService.class, () -> mock(EventStreamService.class));

    @Test
    void communityBuildGetsAllNineDefaults() {
        runner.run(context -> {
            assertThat(context.getBean(PaymentChannel.class)).isInstanceOf(MockPaymentChannel.class);
            assertThat(context.getBean(SettlementService.class)).isInstanceOf(NoopSettlementService.class);
            assertThat(context.getBean(MemberBenefitService.class)).isInstanceOf(NoopMemberBenefitService.class);
            assertThat(context.getBean(ReservationGuard.class)).isInstanceOf(PermitAllReservationGuard.class);
            assertThat(context.getBean(TenantRegistrationService.class))
                    .isInstanceOf(DisabledTenantRegistrationService.class);
            assertThat(context.getBean(TenantMenuScope.class)).isInstanceOf(UnrestrictedTenantMenuScope.class);
            assertThat(context.getBean(TenantConfigSource.class)).isInstanceOf(NoopTenantConfigSource.class);
            assertThat(context.getBean(TenantPaymentProfileProvider.class))
                    .isInstanceOf(NoopTenantPaymentProfileProvider.class);
            assertThat(context.getBean(NotificationChannel.class)).isInstanceOf(InAppNotificationChannel.class);
        });
    }

    @Test
    void platformBeanReplacesTenantScopeDefault() {
        runner.withUserConfiguration(PlatformTenantScopeConfig.class).run(context -> {
            assertThat(context.getBeansOfType(TenantMenuScope.class)).hasSize(1);
            assertThat(context.getBean(TenantMenuScope.class).allowedMenuIds(1L)).containsExactly(10L);
        });
    }

    @Test
    void platformBeanReplacesDefaultImplementation() {
        runner.withUserConfiguration(PlatformPaymentConfig.class).run(context -> {
            assertThat(context.getBeansOfType(PaymentChannel.class)).hasSize(1);
            assertThat(context.getBean(PaymentChannel.class).key()).isEqualTo("wechat");
        });
    }

    @Test
    void extraNotificationChannelsCoexistWithInApp() {
        runner.withUserConfiguration(SmsChannelConfig.class).run(context -> {
            List<NotificationChannel> channels = List.copyOf(context.getBeansOfType(NotificationChannel.class).values());
            assertThat(channels).hasSize(2);
            assertThat(new NotificationDispatcher(channels).channels())
                    .extracting(NotificationChannel::key)
                    .startsWith("inapp");
        });
    }

    @Test
    void mockChannelSucceedsSynchronously() {
        MockPaymentChannel channel = new MockPaymentChannel();
        PayResult pay = channel.pay(new PayRequest(1L, "BC2026", 1L, 1L, 2L, null, "充电订单", "user"));
        RefundResult refund = channel.refund(new RefundRequest(1L, "BC2026", "RF001", 1L, 1L, 2L,
                null, "运营后台模拟退款", "admin"));

        assertThat(pay.success()).isTrue();
        assertThat(pay.channel()).isEqualTo("mock");
        assertThat(pay.tradeNo()).isEqualTo("MOCKBC2026");
        assertThat(refund.success()).isTrue();
        assertThat(refund.refundTradeNo()).isEqualTo("MOCKRRF001");
    }

    @Test
    void defaultsArePassThroughOrClosed() {
        StartGuardQuery guardQuery = new StartGuardQuery(1L, 1L, 1L, 1, 2L);
        SettlementContext settlement = new SettlementContext(1L, "BC2026", 1L, 1L, 2L,
                "COMPLETED", null, null, null, LocalDateTime.now());

        assertThatCode(() -> new PermitAllReservationGuard().assertAllowed(guardQuery)).doesNotThrowAnyException();
        assertThatCode(() -> new NoopSettlementService().afterSettle(settlement)).doesNotThrowAnyException();
        assertThat(new NoopMemberBenefitService()
                .evaluate(new BenefitQuery(2L, 1L, 1L, 1L, 1, null))).isEmpty();
        assertThat(new DisabledTenantRegistrationService().isEnabled()).isFalse();
        assertThatThrownBy(() -> new DisabledTenantRegistrationService().apply(
                new RegistrationApplication("运营商A", "张三", "13800000000", null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("自助入驻未开放");
        // 裁剪与覆盖的默认实现一律「不生效」：社区版读到的是全量菜单与全局配置值
        assertThat(new UnrestrictedTenantMenuScope().allowedMenuIds(1L)).isNull();
        assertThat(new NoopTenantConfigSource().overrideValue(1L, "brand.platformName")).isNull();
    }

    @Test
    void dispatcherIsolatesFailingChannel() {
        NotificationEvent event = event("ALERT");
        List<String> received = new ArrayList<>();
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.<NotificationChannel>of(
                channel("boom", 0, e -> {
                    throw new IllegalStateException("渠道不可用");
                }),
                channel("sms", 10, e -> received.add(e.type()))));

        NotificationEvent out = dispatcher.dispatch(event);

        assertThat(received).containsExactly("ALERT");
        assertThat(out).isSameAs(event);
    }

    @Test
    void dispatcherCarriesMessageIdFromInAppChannelToTheRest() {
        NotificationEvent event = event("ORDER");
        List<Long> seenBySms = new ArrayList<>();
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.<NotificationChannel>of(
                channel("inapp", 0, e -> {
                }).withReturn(event.withMessageId(9527L)),
                channel("sms", 10, e -> seenBySms.add(e.messageId()))));

        dispatcher.dispatch(event);

        assertThat(seenBySms).containsExactly(9527L);
    }

    @Test
    void inAppChannelEnrichesEventWithPersistedId() {
        runner.run(context -> {
            SysMessageMapper mapper = context.getBean(SysMessageMapper.class);
            when(mapper.insert(any(SysMessage.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, SysMessage.class).setId(88L);
                return 1;
            });

            NotificationEvent out = context.getBean(NotificationChannel.class).send(event("ORDER"));

            assertThat(out.messageId()).isEqualTo(88L);
            assertThat(out.type()).isEqualTo("ORDER");
        });
    }

    @Test
    void snapshotJsonOmitsBenefitsWhenCommunity() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RateSnapshot snapshot = new RateSnapshot();
        snapshot.setStrategyId(1L);

        assertThat(mapper.writeValueAsString(snapshot)).doesNotContain("benefits");

        snapshot.setBenefits(List.of(new BenefitItem("VIP", "会员价", "DISCOUNT", null)));
        assertThat(mapper.writeValueAsString(snapshot)).contains("benefits");
    }

    private NotificationEvent event(String type) {
        return new NotificationEvent(null, type, "INFO", 1L, 1L, "标题", "内容", "ORDER", "1",
                LocalDateTime.now());
    }

    private StubChannel channel(String key, int order, Consumer<NotificationEvent> body) {
        return new StubChannel(key, order, body, null);
    }

    private static class StubChannel implements NotificationChannel {

        private final String key;
        private final int order;
        private final Consumer<NotificationEvent> body;
        private final NotificationEvent returned;

        private StubChannel(String key, int order, Consumer<NotificationEvent> body, NotificationEvent returned) {
            this.key = key;
            this.order = order;
            this.body = body;
            this.returned = returned;
        }

        StubChannel(String key, int order, Consumer<NotificationEvent> body) {
            this(key, order, body, null);
        }

        StubChannel withReturn(NotificationEvent event) {
            return new StubChannel(key, order, body, event);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public NotificationEvent send(NotificationEvent event) {
            body.accept(event);
            return returned == null ? event : returned;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlatformPaymentConfig {

        @Bean
        PaymentChannel wechatPaymentChannel() {
            return new PaymentChannel() {
                @Override
                public String key() {
                    return "wechat";
                }

                @Override
                public PayResult pay(PayRequest request) {
                    return PayResult.ok("wechat", "WX" + request.orderNo());
                }

                @Override
                public RefundResult refund(RefundRequest request) {
                    return RefundResult.ok("wechat", "WXR" + request.refundNo());
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlatformTenantScopeConfig {

        @Bean
        TenantMenuScope packageTenantMenuScope() {
            return tenantId -> Set.of(10L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SmsChannelConfig {

        @Bean
        NotificationChannel smsNotificationChannel() {
            return new StubChannel("sms", 100, event -> {
            });
        }
    }
}
