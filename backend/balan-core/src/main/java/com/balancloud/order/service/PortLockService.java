package com.balancloud.order.service;

import com.balancloud.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class PortLockService {

    private static final Logger log = LoggerFactory.getLogger(PortLockService.class);
    private static final String PREFIX = "balan:lock:port:";
    private static final Duration TTL = Duration.ofSeconds(15);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public PortLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String lock(Long chargerId, Integer portNo) {
        String key = key(chargerId, portNo);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new ServiceException(409, "端口正在处理其他操作，请稍后重试");
            }
            return token;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Port lock unavailable {}: {}", key, e.getMessage());
            throw new ServiceException(503, "端口锁服务暂不可用，请稍后重试");
        }
    }

    public void unlock(Long chargerId, Integer portNo, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key(chargerId, portNo)), token);
        } catch (Exception e) {
            log.warn("Failed to release port lock {}: {}", key(chargerId, portNo), e.getMessage());
        }
    }

    private String key(Long chargerId, Integer portNo) {
        return PREFIX + chargerId + ":" + portNo;
    }
}
