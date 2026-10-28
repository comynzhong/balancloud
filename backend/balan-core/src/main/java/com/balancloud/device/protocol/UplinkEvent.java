package com.balancloud.device.protocol;

/**
 * 解码后的上行事件 + 它归属的租户。
 *
 * <p>租户必须随事件一起走，不能靠发布线程里的 {@code TenantContext}：事件监听方一旦改成异步
 * 或换线程执行，ThreadLocal 就读不到了，报文会静默落进默认租户的账本。
 */
public record UplinkEvent(long tenantId, PlatformEvent event) {
}
