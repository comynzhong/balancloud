package com.balancloud.device.ocpp;

import com.balancloud.device.ocpp.config.OcppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * OCPP 下行 uniqueId → 平台 cmdId 的映射。
 *
 * <p>必须放 Redis：指令可能由 A 实例发出，回执从 B 实例持有的会话回来。
 * 放本机 Map 在双实例下必然漏回执，订单会卡在 PENDING_START / STOPPING。
 */
@Service
public class OcppPendingStore {

    private static final Logger log = LoggerFactory.getLogger(OcppPendingStore.class);
    private static final String PREFIX = "balan:ocpp:pending:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OcppProperties properties;

    public OcppPendingStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                            OcppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void register(String identity, String uniqueId, PendingCommand command) {
        if (uniqueId == null || command == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(identity, uniqueId),
                    objectMapper.writeValueAsString(command), properties.getPendingTtl());
        } catch (Exception e) {
            log.warn("Failed to register OCPP pending command {}: {}", uniqueId, e.getMessage());
        }
    }

    public PendingCommand take(String identity, String uniqueId) {
        if (uniqueId == null) {
            return null;
        }
        String key = key(identity, uniqueId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            redisTemplate.delete(key);
            return objectMapper.readValue(value, PendingCommand.class);
        } catch (Exception e) {
            log.warn("Failed to read OCPP pending command {}: {}", uniqueId, e.getMessage());
            return null;
        }
    }

    private String key(String identity, String uniqueId) {
        return PREFIX + identity + ":" + uniqueId;
    }

    /** {@code type} 存指令类型名，回执要带回去给指令状态机。 */
    public record PendingCommand(String cmdId, String type, Integer portNo) {
    }
}
