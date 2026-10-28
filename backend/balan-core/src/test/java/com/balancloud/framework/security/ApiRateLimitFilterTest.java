package com.balancloud.framework.security;

import com.balancloud.system.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitFilterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SystemConfigService configService;
    private ApiRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        configService = mock(SystemConfigService.class);
        chain = mock(FilterChain.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(configService.isRateLimitEnabled()).thenReturn(true);
        when(configService.getRateLimitWindowSeconds()).thenReturn(60);
        when(configService.getRateLimitMaxRequests()).thenReturn(2);
        when(configService.getRateLimitLoginMaxRequests()).thenReturn(1);
        filter = new ApiRateLimitFilter(redisTemplate, configService, new ObjectMapper());
    }

    @Test
    void rejectsRequestOverGeneralLimit() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        MockHttpServletResponse response = invoke("/api/v1/orders", "POST");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("\"code\":429");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void appliesSeparateLoginLimit() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        MockHttpServletResponse response = invoke("/login", "POST");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("1");
        verify(configService).getRateLimitLoginMaxRequests();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void passesWhenUnderLimit() throws Exception {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        MockHttpServletResponse response = invoke("/api/v1/orders", "GET");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void passesWhenDisabledOrRedisUnavailable() throws Exception {
        when(configService.isRateLimitEnabled()).thenReturn(false);
        invoke("/api/v1/orders", "GET");
        verify(chain).doFilter(any(), any());

        when(configService.isRateLimitEnabled()).thenReturn(true);
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));
        MockHttpServletResponse response = invoke("/api/v1/orders", "GET");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse invoke(String path, String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
