package com.balancloud.device.ocpp.service;

import com.balancloud.device.domain.Charger;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.service.ChargingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OCPP 交易与平台订单的接线：订单状态推进在 {@link ChargingService}，交易号映射在
 * {@link OcppTransactionStore}，这里只负责把两边按同一时刻对上。
 *
 * <p>刻意不把 {@code OcppTransactionStore} 塞进 {@code ChargingService}：订单服务不该知道
 * 某个协议需要交易号，否则下一个协议（云快充）又要往订单服务里加一张表。
 */
@Service
public class OcppOrderBridge {

    private final ChargingService chargingService;
    private final OcppTransactionStore transactionStore;
    private final OcppPlugAndChargeService plugAndChargeService;

    public OcppOrderBridge(ChargingService chargingService, OcppTransactionStore transactionStore,
                           OcppPlugAndChargeService plugAndChargeService) {
        this.chargingService = chargingService;
        this.transactionStore = transactionStore;
        this.plugAndChargeService = plugAndChargeService;
    }

    public void onTransactionStarted(Charger charger, Integer connectorId, String idTag,
                                     Double meterStartKwh, long ts) {
        ChargeOrder order = chargingService.onTransactionStarted(charger, connectorId, meterStartKwh, ts);
        if (order == null) {
            // 即插即充没有 RemoteStart：设备直接报 StartTransaction，由 VIN 反查车主后补建订单。
            order = plugAndChargeService.start(charger, connectorId, idTag, ts);
        }
        if (order == null) {
            // 桩上有人手动启动了，但平台没有对应订单：不建映射，回执会给桩 transactionId=0 让它停掉。
            return;
        }
        transactionStore.open(order, charger.getDeviceNo(), connectorId, idTag, meterStartKwh, ts);
    }

    public void onTransactionStopped(Charger charger, Integer connectorId, Long transactionId,
                                     Double meterStopKwh, String stopReason, String rawStopReason,
                                     long ts) {
        // OCPP 1.6J 的 StopTransaction 不带 connectorId，多枪桩停第二枪时只能靠交易号回查。
        Integer portNo = connectorId;
        if ((portNo == null || portNo <= 0) && transactionId != null && transactionId > 0) {
            OcppTransaction tx = transactionStore.findById(transactionId);
            if (tx != null) {
                portNo = tx.getConnectorId();
            }
        }
        chargingService.onTransactionStopped(charger, portNo, meterStopKwh, stopReason, ts);
        if (transactionId != null && transactionId > 0) {
            // 交易表存 OCPP 原文，订单表存平台映射值；两边各自可回放。
            transactionStore.close(charger.getId(), transactionId, meterStopKwh,
                    StringUtils.hasText(rawStopReason) ? rawStopReason : stopReason, ts);
        }
    }
}
