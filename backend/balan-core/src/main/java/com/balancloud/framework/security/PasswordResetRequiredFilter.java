package com.balancloud.framework.security;

import com.balancloud.common.core.AjaxResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 初始密码或管理员重置后的账号只能访问改密相关接口，避免跳过强制改密。
 */
@Component
public class PasswordResetRequiredFilter extends OncePerRequestFilter {

    private static final int CODE_PASSWORD_CHANGE_REQUIRED = 428;

    private static final List<String> ALLOWED_PATHS = List.of(
            "/login", "/logout", "/captchaImage", "/error",
            "/getInfo", "/getRouters",
            "/api/v1/system/profile/password");

    private final ObjectMapper objectMapper;

    public PasswordResetRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (requiresPasswordChange() && !isAllowed(request)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    AjaxResult.error(CODE_PASSWORD_CHANGE_REQUIRED, "请先修改初始密码"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiresPasswordChange() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof LoginUser loginUser
                && loginUser.isPwdResetRequired();
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (ALLOWED_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("/api/v1/public/")
                || path.startsWith("/ws/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
