package com.balancloud.app.security;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AppTokenService {

    private static final Logger log = LoggerFactory.getLogger(AppTokenService.class);

    private static final String TOKEN_PREFIX = "app_tokens:";
    private static final String USER_TOKEN_PREFIX = "app_tokens:user:";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_APP = "app";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecretKey secretKey;
    private final long expireMinutes;

    public AppTokenService(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           @Value("${balan.token.secret}") String secret,
                           @Value("${balan.miniapp.token-expire-minutes:10080}") long expireMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String createToken(AppLoginUser loginUser) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + TimeUnit.MINUTES.toMillis(expireMinutes));

        String token = Jwts.builder()
                .id(uuid)
                .subject(String.valueOf(loginUser.getUserId()))
                .claim(CLAIM_TYPE, TYPE_APP)
                .claim("uid", loginUser.getUserId())
                .issuedAt(new Date(loginUser.getLoginTime()))
                .expiration(new Date(loginUser.getExpireTime()))
                .signWith(secretKey)
                .compact();

        saveToken(uuid, loginUser);
        return token;
    }

    public AppLoginUser getLoginUser(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_APP.equals(claims.get(CLAIM_TYPE, String.class))) {
                return null;
            }
            String jti = claims.getId();
            if (!StringUtils.hasText(jti)) {
                return null;
            }
            String json = redisTemplate.opsForValue().get(TOKEN_PREFIX + claims.getId());
            if (!StringUtils.hasText(json)) {
                return null;
            }
            AppLoginUser loginUser = objectMapper.readValue(json, AppLoginUser.class);
            if (loginUser.getTenantId() == null) {
                loginUser.setTenantId(TenantContext.DEFAULT_TENANT_ID);
            }
            if (loginUser.getExpireTime() < System.currentTimeMillis()) {
                removeToken(claims.getId(), loginUser.getUserId());
                return null;
            }
            return loginUser;
        } catch (JwtException | IllegalArgumentException | JsonProcessingException e) {
            log.debug("Invalid app token: {}", e.getMessage());
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
            AppLoginUser loginUser = getLoginUser(token);
            removeToken(claims.getId(), loginUser == null ? null : loginUser.getUserId());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Ignore invalid app token on logout: {}", e.getMessage());
        }
    }

    /** 停用用户或管理员强制下线时，清掉该用户的全部 App 会话。 */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        String indexKey = USER_TOKEN_PREFIX + userId;
        var jtis = redisTemplate.opsForSet().members(indexKey);
        if (jtis != null) {
            jtis.forEach(jti -> redisTemplate.delete(TOKEN_PREFIX + jti));
        }
        redisTemplate.delete(indexKey);
    }

    private void removeToken(String jti, Long userId) {
        if (StringUtils.hasText(jti)) {
            redisTemplate.delete(TOKEN_PREFIX + jti);
            if (userId != null) {
                redisTemplate.opsForSet().remove(USER_TOKEN_PREFIX + userId, jti);
            }
        }
    }

    private void saveToken(String uuid, AppLoginUser loginUser) {
        try {
            String json = objectMapper.writeValueAsString(loginUser);
            redisTemplate.opsForValue().set(TOKEN_PREFIX + uuid, json, expireMinutes, TimeUnit.MINUTES);
            String indexKey = USER_TOKEN_PREFIX + loginUser.getUserId();
            redisTemplate.opsForSet().add(indexKey, uuid);
            redisTemplate.expire(indexKey, expireMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize app session", e);
        }
    }
}
