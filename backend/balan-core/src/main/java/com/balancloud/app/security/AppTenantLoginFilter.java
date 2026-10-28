package com.balancloud.app.security;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.framework.tenant.TenantRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 小程序登录的租户归属：同一个手机号/openid 在两个运营商下是两个用户，
 * 所以注册与登录必须先确定落在哪个租户，否则多运营商只剩一个租户有 C 端数据。
 *
 * <p>口径和后台的 {@code TenantCodeLoginFilter} 一致：带 {@code X-Tenant-Code} 就按它落，
 * 解析不到直接 400，绝不静默回落默认租户；不带编码保持原行为（默认租户），
 * 社区版与单租户部署逐字节不变。
 *
 * <p>租户号写进会话（{@link com.balancloud.app.security.AppLoginUser#getTenantId()}）后，
 * 后续 {@code /api/app/**} 由 {@link TenantContext} 从会话解析，不用再带这个头。
 */
@Component
public class AppTenantLoginFilter extends OncePerRequestFilter {

    public static final String TENANT_CODE_HEADER = "X-Tenant-Code";

    private static final List<String> LOGIN_PATHS = List.of(
            "/api/app/auth/login", "/api/app/auth/dev-login");

    private final TenantRegistry tenantRegistry;
    private final ObjectMapper objectMapper;

    public AppTenantLoginFilter(TenantRegistry tenantRegistry, ObjectMapper objectMapper) {
        this.tenantRegistry = tenantRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATHS.contains(request.getRequestURI())
                && StringUtils.hasText(request.getHeader(TENANT_CODE_HEADER)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantCode = request.getHeader(TENANT_CODE_HEADER).trim();
        Long tenantId = tenantRegistry.resolveTenantId(tenantCode);
        if (tenantId == null) {
            reject(response, "运营商编码「" + tenantCode + "」不存在或已停用");
            return;
        }
        TenantContext.use(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), AjaxResult.error(400, message));
    }
}
