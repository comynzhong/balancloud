package com.balancloud.framework.spi;

import com.balancloud.order.service.PaymentGateway;
import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.SettlementService;
import com.balancloud.spi.TenantConfigSource;
import com.balancloud.spi.TenantMenuScope;
import com.balancloud.spi.TenantRegistrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 八个 SPI 在本进程里实际生效的实现，用于确认社区版/标准版壳装到了哪一侧。
 */
@Component
public class SpiSnapshotService {

    private final PaymentGateway paymentGateway;
    private final SettlementService settlementService;
    private final MemberBenefitService memberBenefitService;
    private final ReservationGuard reservationGuard;
    private final TenantRegistrationService tenantRegistrationService;
    private final TenantMenuScope tenantMenuScope;
    private final TenantConfigSource tenantConfigSource;
    private final NotificationDispatcher notificationDispatcher;
    private final String edition;

    public SpiSnapshotService(PaymentGateway paymentGateway,
                              SettlementService settlementService,
                              MemberBenefitService memberBenefitService,
                              ReservationGuard reservationGuard,
                              TenantRegistrationService tenantRegistrationService,
                              TenantMenuScope tenantMenuScope,
                              TenantConfigSource tenantConfigSource,
                              NotificationDispatcher notificationDispatcher,
                              @Value("${balan.edition:community}") String edition) {
        this.paymentGateway = paymentGateway;
        this.settlementService = settlementService;
        this.memberBenefitService = memberBenefitService;
        this.reservationGuard = reservationGuard;
        this.tenantRegistrationService = tenantRegistrationService;
        this.tenantMenuScope = tenantMenuScope;
        this.tenantConfigSource = tenantConfigSource;
        this.notificationDispatcher = notificationDispatcher;
        this.edition = edition;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("channels", paymentGateway.channelKeys());
        try {
            payment.put("active", className(paymentGateway.current()));
        } catch (Exception e) {
            payment.put("active", "无匹配通道：" + e.getMessage());
        }

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("channels", notificationDispatcher.channels().stream()
                .map(channel -> channel.key() + "=" + className(channel)).toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("edition", edition);
        result.put("payment", payment);
        result.put("settlement", className(settlementService));
        result.put("memberBenefit", className(memberBenefitService));
        result.put("reservation", className(reservationGuard));
        result.put("tenantRegistration", className(tenantRegistrationService));
        result.put("registrationEnabled", tenantRegistrationService.isEnabled());
        result.put("tenantMenuScope", className(tenantMenuScope));
        result.put("tenantConfigSource", className(tenantConfigSource));
        result.put("notification", notification);
        return result;
    }

    private String className(Object bean) {
        return bean.getClass().getName();
    }
}
