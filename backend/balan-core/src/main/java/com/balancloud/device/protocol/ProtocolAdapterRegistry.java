package com.balancloud.device.protocol;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按 {@code protocol} 建索引的适配器注册表；重名即启动失败。
 *
 * <p>未注册直接抛异常，绝不回落默认协议——回落等于「一个云快充桩被当成 BALAN_MQTT 发指令」，
 * 症状是设备收不到任何东西，比启动失败难查得多。
 */
public class ProtocolAdapterRegistry {

    private final Map<String, DeviceProtocolAdapter> byProtocol;

    public ProtocolAdapterRegistry(List<DeviceProtocolAdapter> adapters) {
        Map<String, DeviceProtocolAdapter> index = new LinkedHashMap<>();
        for (DeviceProtocolAdapter adapter : nullSafe(adapters)) {
            String protocol = normalize(adapter.protocol());
            DeviceProtocolAdapter previous = index.putIfAbsent(protocol, adapter);
            if (previous != null) {
                throw new IllegalStateException("协议重复注册: " + protocol
                        + "（" + previous.getClass().getName() + " 与 " + adapter.getClass().getName() + "）");
            }
        }
        this.byProtocol = Collections.unmodifiableMap(index);
    }

    public DeviceProtocolAdapter require(String protocol) {
        DeviceProtocolAdapter adapter = byProtocol.get(normalize(protocol));
        if (adapter == null) {
            throw new IllegalStateException("协议未注册: " + protocol + "，已注册协议 " + available());
        }
        return adapter;
    }

    public Optional<DeviceProtocolAdapter> find(String protocol) {
        return Optional.ofNullable(byProtocol.get(normalize(protocol)));
    }

    public Set<String> available() {
        return byProtocol.keySet();
    }

    private static String normalize(String protocol) {
        return protocol == null || protocol.isBlank() ? "BALAN_MQTT" : protocol.trim().toUpperCase();
    }

    private static Collection<DeviceProtocolAdapter> nullSafe(List<DeviceProtocolAdapter> adapters) {
        return adapters == null ? List.of() : adapters;
    }
}
