package com.balancloud.device.service;

/**
 * 一条上行报文该交给谁处理：归属哪个租户、用哪个协议解码。
 *
 * <p>归属规则与 {@code resolve} 完全一致——设备不存在或编号跨租户重名就返回 null，
 * 调用方丢报文。宁可丢也不能猜：猜错等于把设备写进别人的账本（§36 的多租户归属修复）。
 */
public record DeviceRouting(Long tenantId, String protocol, String deviceNo) {
}
