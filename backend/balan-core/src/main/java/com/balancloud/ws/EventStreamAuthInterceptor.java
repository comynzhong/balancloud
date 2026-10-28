package com.balancloud.ws;

import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事件流握手鉴权：浏览器 WebSocket 无法带 Authorization 头，
 * 因此按 token 查询参数、Authorization 头、Admin-Token Cookie 依次取令牌，令牌无效直接拒绝握手。
 */
@Component
public class EventStreamAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EventStreamAuthInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TOKEN_COOKIE = Pattern.compile("Admin-Token=([^;]+)");

    private final TokenService tokenService;

    public EventStreamAuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.info("Event stream handshake rejected: no token from {}", request.getRemoteAddress());
            return false;
        }
        LoginUser loginUser = tokenService.getLoginUser(token);
        if (loginUser == null) {
            log.info("Event stream handshake rejected: invalid token for {}", request.getRemoteAddress());
            return false;
        }
        attributes.put("loginUser", loginUser);
        attributes.put("stationIds", new LinkedHashSet<>(loginUser.getStationIds()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String resolveToken(ServerHttpRequest request) {
        String query = request.getURI().getRawQuery();
        if (StringUtils.hasText(query)) {
            for (String pair : Arrays.asList(query.split("&"))) {
                int split = pair.indexOf('=');
                if (split > 0 && "token".equals(pair.substring(0, split))) {
                    return pair.substring(split + 1);
                }
            }
        }
        String header = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String cookieHeader = servletRequest.getServletRequest().getHeader("Cookie");
            if (StringUtils.hasText(cookieHeader)) {
                Matcher matcher = TOKEN_COOKIE.matcher(cookieHeader);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }
}
