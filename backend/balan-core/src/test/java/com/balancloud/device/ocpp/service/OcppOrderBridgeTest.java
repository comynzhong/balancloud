package com.balancloud.device.ocpp.service;

import com.balancloud.device.domain.Charger;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.service.ChargingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多枪桩的 StopTransaction 没有 connectorId：桥接层必须按交易号回查端口，
 * 否则第二枪的停止会落到 1 号口上，订单要等超时才变异常。
 */
class OcppOrderBridgeTest {

    private static final long CHARGER_ID = 58L;
    private static final long TS = 1_793_000_000_000L;

    private ChargingService chargingService;
    private OcppTransactionStore transactionStore;
    private OcppPlugAndChargeService plugAndChargeService;
    private OcppOrderBridge bridge;
    private Charger charger;

    @BeforeEach
    void setUp() {
        chargingService = mock(ChargingService.class);
        transactionStore = mock(OcppTransactionStore.class);
        plugAndChargeService = mock(OcppPlugAndChargeService.class);
        bridge = new OcppOrderBridge(chargingService, transactionStore, plugAndChargeService);
        charger = new Charger();
        charger.setId(CHARGER_ID);
        charger.setDeviceNo("CP-AC-001");
    }

    @Test
    void stopResolvesConnectorFromTheTransactionMapping() {
        OcppTransaction tx = new OcppTransaction();
        tx.setId(42L);
        tx.setChargerId(CHARGER_ID);
        tx.setConnectorId(2);
        when(transactionStore.findById(42L)).thenReturn(tx);

        bridge.onTransactionStopped(charger, 0, 42L, 0.6, "USER_STOP", "Remote", TS);

        verify(chargingService).onTransactionStopped(charger, 2, 0.6, "USER_STOP", TS);
        verify(transactionStore).close(CHARGER_ID, 42L, 0.6, "Remote", TS);
    }

    @Test
    void stopKeepsAnExplicitConnectorWithoutLookingUpTheMapping() {
        bridge.onTransactionStopped(charger, 3, 42L, 0.6, "USER_STOP", "Remote", TS);

        verify(transactionStore, never()).findById(any());
        verify(chargingService).onTransactionStopped(charger, 3, 0.6, "USER_STOP", TS);
    }

    @Test
    void stopWithoutAMappingStillSettlesAndClosesTheTransactionRow() {
        when(transactionStore.findById(42L)).thenReturn(null);

        bridge.onTransactionStopped(charger, 0, 42L, 0.6, "USER_STOP", "Remote", TS);

        verify(chargingService).onTransactionStopped(charger, 0, 0.6, "USER_STOP", TS);
        verify(transactionStore).close(CHARGER_ID, 42L, 0.6, "Remote", TS);
    }

    @Test
    void startFallsBackToPlugAndChargeWhenNoScanOrderExists() {
        ChargeOrder order = new ChargeOrder();
        order.setId(501L);
        when(chargingService.onTransactionStarted(charger, 1, 0.0, TS)).thenReturn(null);
        when(plugAndChargeService.start(charger, 1, "LSVAB2BM5EN123456", TS)).thenReturn(order);

        bridge.onTransactionStarted(charger, 1, "LSVAB2BM5EN123456", 0.0, TS);

        verify(transactionStore).open(eq(order), eq("CP-AC-001"), eq(1), eq("LSVAB2BM5EN123456"),
                eq(0.0), eq(TS));
    }

    @Test
    void startWithNoOrderAnywhereDoesNotOpenAMapping() {
        when(chargingService.onTransactionStarted(charger, 1, 0.0, TS)).thenReturn(null);
        when(plugAndChargeService.start(charger, 1, "UNKNOWN", TS)).thenReturn(null);

        bridge.onTransactionStarted(charger, 1, "UNKNOWN", 0.0, TS);

        verify(transactionStore, never()).open(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
