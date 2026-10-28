package com.balancloud.order.service;

import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderRefund;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderRateDetailMapper;
import com.balancloud.order.mapper.OrderRefundMapper;
import com.balancloud.system.service.SystemConfigService;
import com.balancloud.spi.dto.RefundResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 退款入口（§51.5.6）：通道受理但未到账时订单必须保持 PAID，
 * 只有明确成功才置 REFUNDED；处理中的退款不能被重复发起。
 */
class OrderQueryServiceRefundTest {

    private final ChargeOrderMapper orderMapper = mock(ChargeOrderMapper.class);
    private final OrderRateDetailMapper detailMapper = mock(OrderRateDetailMapper.class);
    private final ChargerMapper chargerMapper = mock(ChargerMapper.class);
    private final StationMapper stationMapper = mock(StationMapper.class);
    private final StationScopeService scopeService = mock(StationScopeService.class);
    private final BillingService billingService = mock(BillingService.class);
    private final AppUserMapper appUserMapper = mock(AppUserMapper.class);
    private final AppVehicleMapper vehicleMapper = mock(AppVehicleMapper.class);
    private final OrderRefundMapper refundMapper = mock(OrderRefundMapper.class);
    private final SystemConfigService configService = mock(SystemConfigService.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final OccupancyService occupancyService = mock(OccupancyService.class);
    private final SensitiveDataMasker masker = mock(SensitiveDataMasker.class);

    private final OrderQueryService service = new OrderQueryService(orderMapper, detailMapper,
            chargerMapper, stationMapper, scopeService, billingService, appUserMapper,
            vehicleMapper, refundMapper, configService, paymentGateway, occupancyService, masker);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.balancloud.order.test");
        TableInfoHelper.initTableInfo(assistant, ChargeOrder.class);
        TableInfoHelper.initTableInfo(assistant, OrderRefund.class);
    }

    @Test
    void pendingRefundKeepsOrderPaidAndRecordsProcessingRefund() {
        ChargeOrder order = paidOrder();
        when(configService.isRefundEnabled()).thenReturn(true);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(refundMapper.selectOne(any())).thenReturn(null);
        when(paymentGateway.refund(any(), anyString(), anyString(), any()))
                .thenReturn(RefundResult.pending("wechat", "WXR1"));

        service.refund(1L, "用户申请退款");

        assertEquals("PAID", order.getPaymentStatus());
        verify(orderMapper, never()).update(any(), any());
        ArgumentCaptor<OrderRefund> captor = ArgumentCaptor.forClass(OrderRefund.class);
        verify(refundMapper).insert(captor.capture());
        assertEquals("PROCESSING", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getRefundNo());
    }

    @Test
    void processingRefundBlocksASecondRequest() {
        when(configService.isRefundEnabled()).thenReturn(true);
        when(orderMapper.selectById(1L)).thenReturn(paidOrder());
        when(refundMapper.selectOne(any())).thenReturn(refund("PROCESSING"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.refund(1L, "再次退款"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("退款正在处理中"));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void successfulRefundRebuildsFailedRowAndFinalizesOrder() {
        ChargeOrder order = paidOrder();
        when(configService.isRefundEnabled()).thenReturn(true);
        when(orderMapper.selectById(1L)).thenReturn(order);
        OrderRefund failed = refund("FAILED");
        when(refundMapper.selectOne(any())).thenReturn(failed);
        when(paymentGateway.refund(any(), anyString(), anyString(), any()))
                .thenReturn(RefundResult.ok("wechat", "WXR2"));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.refund(1L, "重试退款");

        verify(orderMapper).update(any(), any());
        verify(refundMapper).updateById(failed);
        assertEquals("SUCCESS", failed.getStatus());
        assertNotNull(failed.getRefundNo());
    }

    @Test
    void failedChannelRejectsRefundWithoutWritingRow() {
        when(configService.isRefundEnabled()).thenReturn(true);
        when(orderMapper.selectById(1L)).thenReturn(paidOrder());
        when(refundMapper.selectOne(any())).thenReturn(null);
        when(paymentGateway.refund(any(), anyString(), anyString(), any()))
                .thenReturn(RefundResult.failed("wechat", "微信退款未受理：CLOSED"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.refund(1L, "退款"));

        assertEquals(502, error.getCode());
        verify(refundMapper, never()).insert(any(OrderRefund.class));
    }

    @Test
    void refundSwitchOffRejectsBeforeTouchingTheOrder() {
        when(configService.isRefundEnabled()).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.refund(1L, "退款"));

        assertEquals(403, error.getCode());
        verifyNoInteractions(orderMapper);
    }

    private ChargeOrder paidOrder() {
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo("CO202610270001");
        order.setStationId(3L);
        order.setAppUserId(7L);
        order.setStatus("COMPLETED");
        order.setPaymentStatus("PAID");
        order.setTotalFee(new BigDecimal("18.00"));
        return order;
    }

    private OrderRefund refund(String status) {
        OrderRefund refund = new OrderRefund();
        refund.setId(9L);
        refund.setOrderId(1L);
        refund.setRefundNo("RF202610270001");
        refund.setStatus(status);
        return refund;
    }
}
