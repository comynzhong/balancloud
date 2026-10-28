package com.balancloud.device.ocpp;

import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.ocpp.config.OcppProperties;
import com.balancloud.device.vo.DeviceIdentityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OCPP 握手前置校验：identity 已建档、协议是 OCPP16J、全局唯一，认证开启时再验 Basic。
 *
 * <p>这三条必须在握手阶段做完。等到消息进来才校验，桩已经占着连接、日志里也会出现
 * 「未知设备」的噪音，而且拿不到租户归属。
 */
@Component
public class OcppHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_IDENTITY = "ocpp.identity";
    public static final String ATTR_TENANT_ID = "ocpp.tenantId";
    public static final String ATTR_CHARGER_ID = "ocpp.chargerId";

    private static final Logger log = LoggerFactory.getLogger(OcppHandshakeInterceptor.class);
    private static final Pattern IDENTITY = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final String PREFIX = "/ocpp/";

    private final ChargerMapper chargerMapper;
    private final OcppProperties properties;

    public OcppHandshakeInterceptor(ChargerMapper chargerMapper, OcppProperties properties) {
        this.chargerMapper = chargerMapper;
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String identity = identityOf(request.getURI().getPath());
        if (identity == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        List<DeviceIdentityRef> refs;
        try {
            refs = chargerMapper.selectIdentityRefsByDeviceNo(identity);
        } catch (Exception e) {
            log.warn("OCPP identity lookup failed for {}: {}", identity, e.getMessage());
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        if (refs.isEmpty()) {
            log.warn("Rejecting OCPP connection from unknown charge point {}", identity);
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        if (refs.size() > 1) {
            // device_no 只在租户内唯一，跨租户重名时不能猜归属（§48 R3）。
            log.warn("Rejecting OCPP connection from {}: device_no exists in {} tenants", identity, refs.size());
            response.setStatusCode(HttpStatus.CONFLICT);
            return false;
        }
        DeviceIdentityRef ref = refs.get(0);
        if (!OcppProtocol.PROTOCOL.equalsIgnoreCase(ref.getProtocol())) {
            log.warn("Rejecting OCPP connection from {}: protocol is {}", identity, ref.getProtocol());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (properties.isAuthEnabled()
                && !basicAuthMatches(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_IDENTITY, identity);
        attributes.put(ATTR_TENANT_ID, ref.getTenantId());
        attributes.put(ATTR_CHARGER_ID, ref.getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 没有需要释放的资源。
    }

    private String identityOf(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(PREFIX)) {
            return null;
        }
        String identity = path.substring(PREFIX.length());
        if (!StringUtils.hasText(identity) || identity.contains("/") || !IDENTITY.matcher(identity).matches()) {
            return null;
        }
        return identity;
    }

    private boolean basicAuthMatches(String header) {
        if (!StringUtils.hasText(header) || !header.startsWith("Basic ")) {
            return false;
        }
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                (properties.getBasicUser() + ":" + properties.getBasicPassword())
                        .getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));
    }
}
