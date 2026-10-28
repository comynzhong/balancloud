package com.balancloud.device.ocpp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.device.ocpp.domain.OcppTransaction;
import com.balancloud.device.ocpp.mapper.OcppTransactionMapper;
import com.balancloud.order.domain.ChargeOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 交易号与订单号的映射存储。只碰自己的表，不反向依赖订单服务，
 * 否则「适配器 → 本服务 → 订单服务 → 协议注册表 → 适配器」会绕成环。
 */
@Service
public class OcppTransactionStore {

    private static final Logger log = LoggerFactory.getLogger(OcppTransactionStore.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTIVE = "ACTIVE";
    private static final String CLOSED = "CLOSED";

    private final OcppTransactionMapper mapper;

    public OcppTransactionStore(OcppTransactionMapper mapper) {
        this.mapper = mapper;
    }

    public OcppTransaction findActive(Long chargerId, Integer connectorId) {
        if (chargerId == null || connectorId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<OcppTransaction>()
                .eq(OcppTransaction::getChargerId, chargerId)
                .eq(OcppTransaction::getConnectorId, connectorId)
                .eq(OcppTransaction::getStatus, ACTIVE)
                .orderByDesc(OcppTransaction::getId)
                .last("LIMIT 1"));
    }

    public OcppTransaction findActiveAnyTenant(String deviceNo, Integer connectorId) {
        if (!StringUtils.hasText(deviceNo) || connectorId == null) {
            return null;
        }
        return mapper.selectActiveAnyTenant(deviceNo, connectorId);
    }

    public OcppTransaction findById(Long transactionId) {
        return transactionId == null ? null : mapper.selectById(transactionId);
    }

    public OcppTransaction findActiveByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<OcppTransaction>()
                .eq(OcppTransaction::getOrderNo, orderNo)
                .eq(OcppTransaction::getStatus, ACTIVE)
                .orderByDesc(OcppTransaction::getId)
                .last("LIMIT 1"));
    }

    /**
     * 一笔订单只开一次交易。重复 StartTransaction 返回已有交易，不新建、不换号——
     * 换号会让设备后续 MeterValues 对不上账。
     */
    public OcppTransaction open(ChargeOrder order, String deviceNo, Integer connectorId,
                                String idTag, Double meterStartKwh, long ts) {
        OcppTransaction existing = findActive(order.getChargerId(), connectorId);
        if (existing != null) {
            return existing;
        }
        existing = findActiveByOrderNo(order.getOrderNo());
        if (existing != null) {
            return existing;
        }
        OcppTransaction tx = new OcppTransaction();
        tx.setChargerId(order.getChargerId());
        tx.setDeviceNo(deviceNo);
        tx.setConnectorId(connectorId);
        tx.setOrderNo(order.getOrderNo());
        tx.setIdTag(idTag);
        tx.setMeterStart(toWh(meterStartKwh));
        tx.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE));
        tx.setStatus(ACTIVE);
        try {
            mapper.insert(tx);
            return tx;
        } catch (DuplicateKeyException e) {
            // 两台实例同时收到同一笔 StartTransaction 时，唯一键兜住第二笔。
            log.info("Duplicate StartTransaction for order {}, reusing existing transaction", order.getOrderNo());
            return findActiveByOrderNo(order.getOrderNo());
        }
    }

    /**
     * 关闭交易。已关闭的交易返回 null，让调用方跳过重复结算——
     * OCPP 允许设备重发 StopTransaction，结算必须只发生一次。
     */
    public OcppTransaction close(Long chargerId, Long transactionId,
                                 Double meterStopKwh, String reason, long ts) {
        if (transactionId == null) {
            return null;
        }
        OcppTransaction existing = mapper.selectById(transactionId);
        if (existing == null || !existing.getChargerId().equals(chargerId)) {
            return null;
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<OcppTransaction>()
                .eq(OcppTransaction::getId, transactionId)
                .eq(OcppTransaction::getStatus, ACTIVE)
                .set(OcppTransaction::getStatus, CLOSED)
                .set(OcppTransaction::getMeterStop, toWh(meterStopKwh))
                .set(OcppTransaction::getStopTime, LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE))
                .set(OcppTransaction::getStopReason, reason));
        if (updated == 0) {
            return null;
        }
        return mapper.selectById(transactionId);
    }

    private Integer toWh(Double kwh) {
        if (kwh == null) {
            return null;
        }
        return BigDecimal.valueOf(kwh).multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
