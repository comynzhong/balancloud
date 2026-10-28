package com.balancloud.app.service;

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
public class AppActiveOrderGuard {

    private static final Logger log = LoggerFactory.getLogger(AppActiveOrderGuard.class);
    private static final String PREFIX = "balan:lock:app:active:";
    private static final Duration TTL = Duration.ofSeconds(15);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public AppActiveOrderGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String lock(Long userId) {
        String key = PREFIX + userId;
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, TTL);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new ServiceException(409, "正在处理上一笔操作，请稍后重试");
            }
            return token;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("App order lock unavailable {}: {}", key, e.getMessage());
            throw new ServiceException(503, "服务暂不可用，请稍后重试");
        }
    }

    public void unlock(Long userId, String token) {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + userId), token);
        } catch (Exception e) {
            log.warn("Failed to release app order lock for user {}: {}", userId, e.getMessage());
        }
    }
}
