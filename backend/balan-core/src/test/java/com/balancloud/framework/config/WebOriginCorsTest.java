package com.balancloud.framework.config;

import com.balancloud.app.security.AppAuthenticationFilter;
import com.balancloud.framework.security.ApiRateLimitFilter;
import com.balancloud.framework.security.JwtAuthenticationFilter;
import com.balancloud.framework.security.PasswordResetRequiredFilter;
import com.balancloud.framework.security.RestAccessDeniedHandler;
import com.balancloud.framework.security.RestAuthenticationEntryPoint;
import com.balancloud.ws.EventStreamAuthInterceptor;
import com.balancloud.ws.EventStreamHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §61 的反证：浏览器对同源 POST 也发 Origin，来源不在白名单时 Spring 直接 403「Invalid CORS request」。
 * 所以这里断言的是真实 {@link DefaultCorsProcessor} 的判定结果，不是配置项回读。
 */
class WebOriginCorsTest {

    // 192.0.2.0/24 是 RFC 5737 保留的文档地址段：示例来源不该写真实公网 IP（§61.3）
    private static final String PUBLIC_ORIGIN = "http://192.0.2.10:5174";

    @Test
    void postFromConfiguredOriginIsAccepted() throws Exception {
        MockHttpServletResponse response = process(List.of("http://localhost:*", "http://192.0.2.10:*"), PUBLIC_ORIGIN);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(PUBLIC_ORIGIN);
    }

    @Test
    void postFromUnlistedOriginIsStillRejected() throws Exception {
        MockHttpServletResponse response = process(List.of("http://localhost:*", "http://192.0.2.10:*"),
                "http://evil.example.com");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid CORS request");
    }

    @Test
    void localhostStaysAllowedWithDefaultPatterns() throws Exception {
        MockHttpServletResponse response = process(new WebOriginProperties().getAllowedOriginPatterns(),
                "http://localhost:5174");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void commaSeparatedPropertyBindsToList() {
        WebOriginProperties properties = new WebOriginProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("balan.security.allowed-origin-patterns",
                        "http://localhost:*,http://192.0.2.10:*");
        new Binder(ConfigurationPropertySources.from(environment.getPropertySources()))
                .bind("balan.security", Bindable.ofInstance(properties));

        assertThat(properties.getAllowedOriginPatterns())
                .containsExactly("http://localhost:*", "http://192.0.2.10:*");
    }

    @Test
    void eventStreamHandshakeUsesTheSameAllowList() {
        EventStreamHandler handler = mock(EventStreamHandler.class);
        EventStreamAuthInterceptor interceptor = mock(EventStreamAuthInterceptor.class);
        WebOriginProperties properties = new WebOriginProperties();
        properties.setAllowedOriginPatterns(List.of("http://localhost:*", "http://192.0.2.10:*"));
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class, RETURNS_SELF);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        when(registry.addHandler(handler, "/ws/events")).thenReturn(registration);

        new WebSocketConfig(handler, interceptor, properties).registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns("http://localhost:*", "http://192.0.2.10:*");
    }

    private MockHttpServletResponse process(List<String> patterns, String origin) throws IOException {
        WebOriginProperties properties = new WebOriginProperties();
        properties.setAllowedOriginPatterns(patterns);
        CorsConfigurationSource source = new SecurityConfig(
                mock(ApiRateLimitFilter.class),
                mock(JwtAuthenticationFilter.class),
                mock(AppAuthenticationFilter.class),
                mock(PasswordResetRequiredFilter.class),
                mock(RestAuthenticationEntryPoint.class),
                mock(RestAccessDeniedHandler.class),
                properties).corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
