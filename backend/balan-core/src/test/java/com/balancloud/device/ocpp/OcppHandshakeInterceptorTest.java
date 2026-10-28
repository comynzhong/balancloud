package com.balancloud.device.ocpp;

import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.ocpp.config.OcppProperties;
import com.balancloud.device.vo.DeviceIdentityRef;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 握手阶段的四道门：identity 格式、已建档、协议、全局唯一（认证开启时再加 Basic）。
 * 这些必须发生在连接建立前，否则拿不到租户归属，后面的上行事件都无处可去。
 */
class OcppHandshakeInterceptorTest {

    private static final String IDENTITY = "CP-DC-001";

    private final ChargerMapper chargerMapper = mock(ChargerMapper.class);
    private final OcppProperties properties = new OcppProperties();
    private final OcppHandshakeInterceptor interceptor =
            new OcppHandshakeInterceptor(chargerMapper, properties);

    @Test
    void acceptsAKnownOcppChargePointAndStampsTenantAttributes() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY)).thenReturn(List.of(ref(9L, 3L, "OCPP16J")));

        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse raw = new MockHttpServletResponse();

        assertThat(handshake("/ocpp/" + IDENTITY, null, attributes, raw)).isTrue();
        assertThat(attributes)
                .containsEntry(OcppHandshakeInterceptor.ATTR_IDENTITY, IDENTITY)
                .containsEntry(OcppHandshakeInterceptor.ATTR_TENANT_ID, 3L)
                .containsEntry(OcppHandshakeInterceptor.ATTR_CHARGER_ID, 9L);
    }

    @Test
    void rejectsUnknownOrMalformedIdentities() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(anyString())).thenReturn(List.of());

        assertStatus("/ocpp/CP-UNKNOWN", null, 404);
        // 路径里带 / 或非法字符在查库前就拒绝
        assertStatus("/ocpp/bad/id", null, 400);
        assertStatus("/ocpp/", null, 400);
        assertStatus("/elsewhere/CP-DC-001", null, 400);
    }

    @Test
    void rejectsTheSameIdentityRegisteredByTwoTenants() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY))
                .thenReturn(List.of(ref(1L, 1L, "OCPP16J"), ref(2L, 2L, "OCPP16J")));

        assertStatus("/ocpp/" + IDENTITY, null, 409);
    }

    @Test
    void rejectsAKnownDeviceThatIsNotUsingOcpp() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY))
                .thenReturn(List.of(ref(9L, 3L, "BALAN_MQTT")));

        assertStatus("/ocpp/" + IDENTITY, null, 403);
    }

    @Test
    void basicAuthIsOptionalByDefault() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY)).thenReturn(List.of(ref(9L, 3L, "OCPP16J")));

        assertStatus("/ocpp/" + IDENTITY, null, 200);
    }

    @Test
    void basicAuthChecksTheConfiguredCredentialsWhenEnabled() {
        properties.setAuthEnabled(true);
        properties.setBasicUser("balan");
        properties.setBasicPassword("s3cret");
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY)).thenReturn(List.of(ref(9L, 3L, "OCPP16J")));

        assertStatus("/ocpp/" + IDENTITY, null, 401);
        assertStatus("/ocpp/" + IDENTITY, basic("balan", "wrong"), 401);
        assertStatus("/ocpp/" + IDENTITY, basic("balan", "s3cret"), 200);
    }

    @Test
    void databaseFailureFailsClosedInsteadOfTreatingThePointAsUnknown() {
        when(chargerMapper.selectIdentityRefsByDeviceNo(IDENTITY))
                .thenThrow(new IllegalStateException("db down"));

        assertStatus("/ocpp/" + IDENTITY, null, 503);
    }

    private void assertStatus(String path, String authorization, int expected) {
        MockHttpServletResponse raw = new MockHttpServletResponse();
        handshake(path, authorization, new HashMap<>(), raw);
        assertThat(raw.getStatus()).isEqualTo(expected);
    }

    private boolean handshake(String path, String authorization,
                              Map<String, Object> attributes, MockHttpServletResponse raw) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            servletRequest.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServerHttpResponse response = new ServletServerHttpResponse(raw);
        return interceptor.beforeHandshake(request, response, null, attributes);
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static DeviceIdentityRef ref(Long id, Long tenantId, String protocol) {
        DeviceIdentityRef ref = new DeviceIdentityRef();
        ref.setId(id);
        ref.setTenantId(tenantId);
        ref.setDeviceNo(IDENTITY);
        ref.setProtocol(protocol);
        return ref;
    }
}
