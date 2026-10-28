package com.balancloud.framework.security;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 基于 Redis 固定窗口的接口限流：普通接口与登录接口分开计数，按客户端 IP 隔离。
 * Redis 不可用时放行，避免限流组件成为单点故障。
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    private static final List<String> LOGIN_PATHS = List.of(
            "/login", "/api/app/auth/login", "/api/app/auth/dev-login");

    private static final String KEY_PREFIX = "balan:rate:";

    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(StringRedisTemplate redisTemplate,
                              SystemConfigService configService,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/ws/") || path.startsWith("/error")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            return true;
        }
        return !path.startsWith("/api/") && !LOGIN_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!configService.isRateLimitEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean login = LOGIN_PATHS.contains(request.getRequestURI());
        int limit = login ? configService.getRateLimitLoginMaxRequests()
                : configService.getRateLimitMaxRequests();
        int windowSeconds = configService.getRateLimitWindowSeconds();
        long now = Instant.now().getEpochSecond();
        long bucket = now / windowSeconds;
        String key = KEY_PREFIX + (login ? "login:" : "api:") + clientIp(request) + ":" + bucket;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 2L));
            long used = count == null ? 0 : count;
            long remaining = Math.max(0, limit - used);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            if (used > limit) {
                long retryAfter = windowSeconds - (now % windowSeconds);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        AjaxResult.error(429, "请求过于频繁，请 " + retryAfter + " 秒后重试"));
                return;
            }
        } catch (Exception e) {
            log.warn("限流计数失败，按放行处理：{}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (configService.isRateLimitTrustForwarded()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String first = forwarded.split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return sanitize(first);
                }
            }
        }
        return sanitize(request.getRemoteAddr());
    }

    private String sanitize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        String value = ip.trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
