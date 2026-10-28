package com.balancloud.device.protocol;

import java.util.List;

/**
 * 平台只认这几种上行事件，取代 {@code switch (String event)}。
 *
 * <p>sealed 是刻意的：厂商事件名拼错原本会被 default 分支静默丢弃，
 * 现在要么归进 {@link Unrecognized}，要么在编译期就要求适配器补一个分支。
 */
public sealed interface PlatformEvent {

    /** 所有事件都带设备号，统一提到接口上，免得调用方为了取设备号再写一遍 instanceof。 */
    String deviceNo();

    record Connectivity(String deviceNo, boolean online, long ts) implements PlatformEvent {
    }

    record MetricReport(String deviceNo, long ts, List<PortSample> ports) implements PlatformEvent {
    }

    record VendorAlert(String deviceNo, Integer portNo, AlertHint hint, String rawType,
                       String message, Double value) implements PlatformEvent {
    }

    /**
     * {@code rawResult} 保留厂商原文（内置协议里是 {@code SUCCESS/FAILED/…}）：
     * 失败文案由平台拼，不能把平台的中文模板塞进适配器，也不能只留一个 boolean 让原文消失。
     */
    record CommandAck(String deviceNo, String cmdId, Integer portNo, String type, boolean success,
                      String rawResult, String message, long ts) implements PlatformEvent {
    }

    record OrderLifecycle(String deviceNo, Integer portNo, String orderNo,
                          String stopReason, Double energyTotal, long ts) implements PlatformEvent {
    }

    /**
     * 端口状态变了，但还没到能计费的程度（OCPP StatusNotification）。
     * 状态值必须是平台四值之一，映射在适配器里做完。
     */
    record PortStatus(String deviceNo, Integer connectorId, String status,
                      String errorCode, long ts) implements PlatformEvent {
    }

    /**
     * 设备侧真正开始了一笔交易（OCPP StartTransaction）。这之前订单一直是 PENDING_START：
     * 远程启动的 ack 只代表「桩接受了请求」，不代表团充电已经开始。
     *
     * <p>{@code meterStartKwh} 已由适配器统一成平台单位 kWh，OCPP 原始的 Wh 不往下游传。
     */
    record TransactionStarted(String deviceNo, Integer connectorId, String idTag,
                              Double meterStartKwh, long ts) implements PlatformEvent {
    }

    /**
     * 设备侧结束了交易（OCPP StopTransaction）；{@code transactionId} 是平台下发给桩的那个整数。
     *
     * <p>{@code stopReason} 已映射成平台词汇（订单用），{@code rawStopReason} 保留 OCPP 原文
     * （交易表用）——「EmergencyStop」和「Local」映射后不再可区分，回放凭据不能只剩映射值。
     */
    record TransactionStopped(String deviceNo, Integer connectorId, Long transactionId,
                              Double meterStopKwh, String stopReason, String rawStopReason,
                              long ts) implements PlatformEvent {
    }

    /** 解不出归属或语义的帧：带原因进日志，不当成「没有这条报文」。 */
    record Unrecognized(String deviceNo, String reason) implements PlatformEvent {
    }
}
