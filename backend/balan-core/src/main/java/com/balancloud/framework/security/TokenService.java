package com.balancloud.framework.security;

import com.balancloud.framework.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final String LOGIN_TOKEN_PREFIX = "login_tokens:";
    private static final String USER_TOKEN_INDEX_PREFIX = "login_tokens:user:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecretKey secretKey;
    private final long expireMinutes;

    public TokenService(StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        @Value("${balan.token.secret}") String secret,
                        @Value("${balan.token.expire-minutes:720}") long expireMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String createToken(LoginUser loginUser) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + TimeUnit.MINUTES.toMillis(expireMinutes));

        String token = Jwts.builder()
                .id(uuid)
                .subject(loginUser.getUsername())
                .claim("userId", loginUser.getUserId())
                .issuedAt(new Date(loginUser.getLoginTime()))
                .expiration(new Date(loginUser.getExpireTime()))
                .signWith(secretKey)
                .compact();

        saveLoginUser(uuid, loginUser);
        return token;
    }

    public LoginUser getLoginUser(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String uuid = claims.getId();
            String json = redisTemplate.opsForValue().get(LOGIN_TOKEN_PREFIX + uuid);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            LoginUser loginUser = objectMapper.readValue(json, LoginUser.class);
            if (loginUser.getTenantId() == null) {
                loginUser.setTenantId(TenantContext.DEFAULT_TENANT_ID);
            }
            if (loginUser.getExpireTime() < System.currentTimeMillis()) {
                redisTemplate.delete(LOGIN_TOKEN_PREFIX + uuid);
                return null;
            }
            return loginUser;
        } catch (JwtException | IllegalArgumentException | JsonProcessingException e) {
            log.debug("Invalid login token: {}", e.getMessage());
            return null;
        }
    }

    public void deleteToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            redisTemplate.delete(LOGIN_TOKEN_PREFIX + claims.getId());
            Object userId = claims.get("userId");
            if (userId != null) {
                redisTemplate.opsForSet().remove(USER_TOKEN_INDEX_PREFIX + userId, claims.getId());
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Ignore invalid token on logout: {}", e.getMessage());
        }
    }

    /**
     * 停用账号或重置密码后立即失效该用户的全部会话，否则 Redis 中的 LoginUser 缓存会撑到过期时间。
     */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        String indexKey = USER_TOKEN_INDEX_PREFIX + userId;
        Set<String> uuids = redisTemplate.opsForSet().members(indexKey);
        if (uuids != null && !uuids.isEmpty()) {
            List<String> keys = uuids.stream().map(uuid -> LOGIN_TOKEN_PREFIX + uuid).toList();
            redisTemplate.delete(keys);
            log.info("Evicted {} active session(s) for user {}", keys.size(), userId);
        }
        redisTemplate.delete(indexKey);
    }

    private void saveLoginUser(String uuid, LoginUser loginUser) {
        try {
            String json = objectMapper.writeValueAsString(loginUser);
            redisTemplate.opsForValue().set(LOGIN_TOKEN_PREFIX + uuid, json, expireMinutes, TimeUnit.MINUTES);
            String indexKey = USER_TOKEN_INDEX_PREFIX + loginUser.getUserId();
            redisTemplate.opsForSet().add(indexKey, uuid);
            redisTemplate.expire(indexKey, expireMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize login session", e);
        }
    }
}
