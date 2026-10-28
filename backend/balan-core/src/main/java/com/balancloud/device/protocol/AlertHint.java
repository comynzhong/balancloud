package com.balancloud.device.protocol;

/**
 * 告警的分类结果。
 *
 * <p>{@code recognized=false} 表示厂商类型在平台的 {@code AlertType} 里没有对应项：
 * 平台必须知道「这是没映射上的厂商码」而不是被静默降级成 PORT_FAULT，
 * {@code vendorType} 仍会进告警正文。
 */
public record AlertHint(String vendorType, String alertTypeName, String level, boolean recognized) {
}
