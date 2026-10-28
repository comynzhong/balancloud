package com.balancloud.device.service;

import com.balancloud.device.mapper.ChargerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 {@code device_no} 反查设备归属租户。
 *
 * <p>MQTT 上行与仿真器下行都没有 HTTP 会话，不先定位租户就会全部落到默认租户，
 * 第二个租户的设备影子会写进租户 1 的键（§25.8 留给 T1 的洞）。
 *
 * <p>{@code device_no} 只在租户内唯一，所以同一编号可能属于多个租户。真出现歧义时
 * 宁可丢报文也不猜租户：猜错就是把设备写进别人的账本。
 */
@Service
public class DeviceTenantResolver {

    private static final Logger log = LoggerFactory.getLogger(DeviceTenantResolver.class);
    private static final long CACHE_TTL_MS = 5 * 60_000L;
    private static final String DEFAULT_PROTOCOL = "BALAN_MQTT";

    private final ChargerMapper chargerMapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, ProtocolCached> protocolCache = new ConcurrentHashMap<>();

    public DeviceTenantResolver(ChargerMapper chargerMapper) {
        this.chargerMapper = chargerMapper;
    }

    /** 设备不存在、或编号在多个租户下都有时返回 null，调用方按「无法归属」处理。 */
    public Long resolve(String deviceNo) {
        if (!StringUtils.hasText(deviceNo)) {
            return null;
        }
        long now = System.currentTimeMillis();
        Cached cached = cache.get(deviceNo);
        if (cached != null && now - cached.atMs() < CACHE_TTL_MS) {
            return cached.tenantId();
        }
        Long tenantId = lookup(deviceNo);
        if (tenantId != null) {
            cache.put(deviceNo, new Cached(tenantId, now));
        } else {
            // 负结果不落缓存：设备可能刚建号，缓存住就永远认不出它。
            cache.remove(deviceNo);
        }
        return tenantId;
    }

    /**
     * 报文该交给谁：租户 + 用哪个协议解码。归属规则和 {@link #resolve} 一模一样。
     *
     * <p>协议列可能为空（早期建的设备没填），为空按内置协议处理——这是现状行为的延续，
     * 不是给未知设备猜一个协议。
     */
    public DeviceRouting route(String deviceNo) {
        Long tenantId = resolve(deviceNo);
        if (tenantId == null) {
            return null;
        }
        return new DeviceRouting(tenantId, protocolOf(deviceNo), deviceNo);
    }

    private String protocolOf(String deviceNo) {
        long now = System.currentTimeMillis();
        ProtocolCached cached = protocolCache.get(deviceNo);
        if (cached != null && now - cached.atMs() < CACHE_TTL_MS) {
            return cached.protocol();
        }
        String protocol;
        try {
            String column = chargerMapper.selectProtocolByDeviceNo(deviceNo);
            protocol = StringUtils.hasText(column) ? column.trim().toUpperCase() : DEFAULT_PROTOCOL;
        } catch (Exception e) {
            log.warn("Protocol lookup for device {} failed: {}", deviceNo, e.getMessage());
            return DEFAULT_PROTOCOL;
        }
        protocolCache.put(deviceNo, new ProtocolCached(protocol, now));
        return protocol;
    }

    public void evict(String deviceNo) {
        if (StringUtils.hasText(deviceNo)) {
            cache.remove(deviceNo);
            protocolCache.remove(deviceNo);
        }
    }

    public void evictAll() {
        cache.clear();
        protocolCache.clear();
    }

    private Long lookup(String deviceNo) {
        List<Long> tenantIds;
        try {
            tenantIds = chargerMapper.selectTenantIdsByDeviceNo(deviceNo);
        } catch (Exception e) {
            log.warn("Tenant lookup for device {} failed: {}", deviceNo, e.getMessage());
            return null;
        }
        if (tenantIds == null || tenantIds.isEmpty()) {
            return null;
        }
        if (tenantIds.size() > 1) {
            log.warn("device_no {} exists in tenants {}, cannot attribute the message to one of them",
                    deviceNo, tenantIds);
            return null;
        }
        return tenantIds.get(0);
    }

    private record Cached(Long tenantId, long atMs) {
    }

    private record ProtocolCached(String protocol, long atMs) {
    }
}
