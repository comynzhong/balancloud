package com.balancloud.device.ocpp.service;

import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.mapper.OcppTransactionMapper;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.support.TableInfoSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易号与订单的映射规则：一笔订单只开一次、只关一次，单位换算只在入口做一次。
 */
class OcppTransactionStoreTest {

    private static final long TS = Instant.parse("2026-10-27T02:00:00Z").toEpochMilli();

    private final OcppTransactionMapper mapper = mock(OcppTransactionMapper.class);
    private final OcppTransactionStore store = new OcppTransactionStore(mapper);

    @BeforeAll
    static void initTableInfo() {
        TableInfoSupport.init(OcppTransaction.class);
    }

    @Test
    void openCreatesAnActiveTransactionAndConvertsKwhToWh() {
        ChargeOrder order = order(7L, "ORD-1");
        doAnswer(invocation -> {
            OcppTransaction tx = invocation.getArgument(0);
            tx.setId(101L);
            return 1;
        }).when(mapper).insert(any(OcppTransaction.class));

        OcppTransaction tx = store.open(order, "CP-DC-001", 1, "BALAN", 1.2, TS);

        assertThat(tx.getId()).isEqualTo(101L);
        assertThat(tx.getChargerId()).isEqualTo(7L);
        assertThat(tx.getOrderNo()).isEqualTo("ORD-1");
        assertThat(tx.getConnectorId()).isEqualTo(1);
        assertThat(tx.getMeterStart()).isEqualTo(1200);
        assertThat(tx.getStatus()).isEqualTo("ACTIVE");
        assertThat(tx.getStartTime()).isEqualTo(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(TS), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void repeatedStartTransactionReusesTheActiveTransaction() {
        OcppTransaction existing = transaction(55L, 7L, "ORD-1");
        when(mapper.selectOne(any())).thenReturn(existing);

        OcppTransaction tx = store.open(order(7L, "ORD-2"), "CP-DC-001", 1, "BALAN", 0.0, TS);

        assertThat(tx).isSameAs(existing);
        verify(mapper, never()).insert(any(OcppTransaction.class));
    }

    @Test
    void openFallsBackToTheExistingOrderWhenAnotherInstanceWonTheInsert() {
        OcppTransaction existing = transaction(66L, 7L, "ORD-1");
        // 第一次按 charger+connector 查、第二次按 orderNo 查都为空，唯一键冲突后再查才命中
        when(mapper.selectOne(any())).thenReturn(null, null, existing);
        doThrow(new DuplicateKeyException("uk_ocpp_transaction_order_no"))
                .when(mapper).insert(any(OcppTransaction.class));

        OcppTransaction tx = store.open(order(7L, "ORD-1"), "CP-DC-001", 1, "BALAN", 0.0, TS);

        assertThat(tx).isSameAs(existing);
    }

    @Test
    void closeRefusesATransactionFromAnotherCharger() {
        OcppTransaction existing = transaction(77L, 9L, "ORD-1");
        when(mapper.selectById(77L)).thenReturn(existing);

        assertThat(store.close(7L, 77L, 2.5, "USER_STOP", TS)).isNull();
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void closeWritesMeterStopInWhAndReturnsTheClosedRow() {
        OcppTransaction active = transaction(88L, 7L, "ORD-1");
        OcppTransaction closed = transaction(88L, 7L, "ORD-1");
        closed.setStatus("CLOSED");
        closed.setMeterStop(600);
        when(mapper.selectById(88L)).thenReturn(active, closed);
        when(mapper.update(any(), any())).thenReturn(1);

        OcppTransaction result = store.close(7L, 88L, 0.6, "USER_STOP", TS);

        assertThat(result).isSameAs(closed);
        assertThat(result.getMeterStop()).isEqualTo(600);
    }

    @Test
    void duplicateStopTransactionDoesNotSettleTwice() {
        OcppTransaction active = transaction(99L, 7L, "ORD-1");
        when(mapper.selectById(99L)).thenReturn(active);
        when(mapper.update(any(), any())).thenReturn(0);

        assertThat(store.close(7L, 99L, 0.6, "USER_STOP", TS)).isNull();
    }

    private static ChargeOrder order(Long chargerId, String orderNo) {
        ChargeOrder order = new ChargeOrder();
        order.setChargerId(chargerId);
        order.setOrderNo(orderNo);
        return order;
    }

    private static OcppTransaction transaction(Long id, Long chargerId, String orderNo) {
        OcppTransaction tx = new OcppTransaction();
        tx.setId(id);
        tx.setChargerId(chargerId);
        tx.setDeviceNo("CP-DC-001");
        tx.setConnectorId(1);
        tx.setOrderNo(orderNo);
        tx.setStatus("ACTIVE");
        return tx;
    }
}
