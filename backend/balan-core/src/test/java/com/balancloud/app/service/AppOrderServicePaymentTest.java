package com.balancloud.app.service;

import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.support.TableInfoSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 支付落账语义（§51.5.3）：预支付成功不代表已收款，只有确认收款的结果才把订单置 PAID。
 */
class AppOrderServicePaymentTest {

    private final ChargeOrderMapper orderMapper = mock(ChargeOrderMapper.class);

    private final AppOrderService service = new AppOrderService(
            orderMapper, null, null, null, null, null, null, null, null, null);

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(ChargeOrder.class);
    }

    @Test
    void pendingPrepayLeavesOrderUnpaid() {
        service.markPaid(1L, "user", PayResult.pending("wechat", "WX1", Map.of("paySign", "SIGN")));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void confirmedPaymentMarksOrderPaid() {
        service.markPaid(1L, "user", PayResult.ok("mock", "MOCK1"));

        verify(orderMapper).update(isNull(), any());
    }
}
