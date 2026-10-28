package com.balancloud.spi.defaults;

import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.NotificationChannel;
import com.balancloud.spi.PaymentChannel;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.TenantConfigSource;
import com.balancloud.spi.TenantMenuScope;
import com.balancloud.spi.TenantPaymentProfileProvider;
import com.balancloud.spi.TenantRegistrationService;
import com.balancloud.system.mapper.SysMessageMapper;
import com.balancloud.ws.EventStreamService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * SPI 社区版默认实现。放在自动配置里而不是组件扫描，是为了让 {@code @ConditionalOnMissingBean}
 * 一定晚于业务 bean 评估——标准版 jar 注册的实现因此能稳定覆盖这里的默认实现。
 * 通知渠道是唯一例外：短信/订阅消息渠道与站内信共存，所以站内信按 bean 名判断是否替换。
 */
@AutoConfiguration
public class SpiDefaultsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PaymentChannel.class)
    public PaymentChannel mockPaymentChannel() {
        return new MockPaymentChannel();
    }

    @Bean
    @ConditionalOnMissingBean(SettlementService.class)
    public SettlementService noopSettlementService() {
        return new NoopSettlementService();
    }

    @Bean
    @ConditionalOnMissingBean(MemberBenefitService.class)
    public MemberBenefitService noopMemberBenefitService() {
        return new NoopMemberBenefitService();
    }

    @Bean
    @ConditionalOnMissingBean(ReservationGuard.class)
    public ReservationGuard permitAllReservationGuard() {
        return new PermitAllReservationGuard();
    }

    @Bean
    @ConditionalOnMissingBean(TenantRegistrationService.class)
    public TenantRegistrationService disabledTenantRegistrationService() {
        return new DisabledTenantRegistrationService();
    }

    @Bean
    @ConditionalOnMissingBean(TenantMenuScope.class)
    public TenantMenuScope unrestrictedTenantMenuScope() {
        return new UnrestrictedTenantMenuScope();
    }

    @Bean
    @ConditionalOnMissingBean(TenantConfigSource.class)
    public TenantConfigSource noopTenantConfigSource() {
        return new NoopTenantConfigSource();
    }

    @Bean
    @ConditionalOnMissingBean(TenantPaymentProfileProvider.class)
    public TenantPaymentProfileProvider noopTenantPaymentProfileProvider() {
        return new NoopTenantPaymentProfileProvider();
    }

    @Bean
    @ConditionalOnMissingBean(name = "inAppNotificationChannel")
    public NotificationChannel inAppNotificationChannel(SysMessageMapper messageMapper,
                                                        EventStreamService eventStreamService) {
        return new InAppNotificationChannel(messageMapper, eventStreamService);
    }
}
