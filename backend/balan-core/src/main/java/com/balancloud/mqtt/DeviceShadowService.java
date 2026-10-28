package com.balancloud.mqtt;

import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.mqtt.model.PortMetric;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DeviceShadowService {

    private static final Logger log = LoggerFactory.getLogger(DeviceShadowService.class);
    private static final String SHADOW_PREFIX = "balan:shadow:";
    private static final String ONLINE_PREFIX = "balan:online:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DeviceShadowService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(String deviceNo, Collection<PortMetric> ports) {
        String key = shadowKey(deviceNo);
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (PortMetric port : ports) {
                if (port.getPortNo() != null) {
                    values.put(String.valueOf(port.getPortNo()), objectMapper.writeValueAsString(port));
                }
            }
            redisTemplate.delete(key);
            if (!values.isEmpty()) {
                redisTemplate.opsForHash().putAll(key, values);
                redisTemplate.expire(key, Duration.ofMinutes(30));
            }
        } catch (Exception e) {
            log.warn("Failed to save device shadow {}: {}", deviceNo, e.getMessage());
        }
    }

    public Map<Integer, PortMetric> get(String deviceNo) {
        Map<Integer, PortMetric> result = new LinkedHashMap<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(shadowKey(deviceNo));
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                PortMetric metric = objectMapper.readValue(String.valueOf(entry.getValue()), PortMetric.class);
                if (metric.getPortNo() != null) {
                    result.put(metric.getPortNo(), metric);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read device shadow {}: {}", deviceNo, e.getMessage());
        }
        return result;
    }

    public void setOnline(String deviceNo, boolean online) {
        try {
            redisTemplate.opsForValue().set(
                    onlineKey(deviceNo),
                    online ? "1" : "0",
                    online ? Duration.ofMinutes(3) : Duration.ofMinutes(30)
            );
        } catch (Exception e) {
            log.warn("Failed to update online cache {}: {}", deviceNo, e.getMessage());
        }
    }

    public void delete(String deviceNo) {
        try {
            redisTemplate.delete(shadowKey(deviceNo));
            redisTemplate.delete(onlineKey(deviceNo));
        } catch (Exception e) {
            log.warn("Failed to delete device shadow {}: {}", deviceNo, e.getMessage());
        }
    }

    /** device_no 从全局唯一变成租户内唯一，缓存键必须带上租户段，否则两个租户的同编号设备会互相覆盖影子。 */
    private String shadowKey(String deviceNo) {
        return SHADOW_PREFIX + TenantContext.get() + ":" + deviceNo;
    }

    private String onlineKey(String deviceNo) {
        return ONLINE_PREFIX + TenantContext.get() + ":" + deviceNo;
    }
}
