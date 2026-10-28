package com.balancloud.app.security;

import com.balancloud.app.domain.AppUser;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.framework.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AppAuthenticationFilter extends OncePerRequestFilter {

    private static final String APP_PATH_PREFIX = "/api/app/";

    private final AppTokenService tokenService;
    private final AppUserMapper userMapper;

    public AppAuthenticationFilter(AppTokenService tokenService, AppUserMapper userMapper) {
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(APP_PATH_PREFIX)) {
            // 小程序接口只认 App token：后台 token 已由 JWT 过滤器认证，必须在这里失效，避免越权
            Authentication existing = SecurityContextHolder.getContext().getAuthentication();
            if (existing != null && !(existing.getPrincipal() instanceof AppLoginUser)) {
                SecurityContextHolder.clearContext();
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                AppLoginUser loginUser = tokenService.getLoginUser(resolveToken(request));
                if (loginUser != null) {
                    // 会话里带着租户号，但此刻 SecurityContext 还没有主体，TenantContext 只能落回默认租户；
                    // 不先切过去，非默认租户的用户会被租户插件过滤掉，鉴权直接失败并误清会话。
                    TenantContext.use(loginUser.getTenantId());
                    try {
                        if (isActive(loginUser.getUserId())) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(loginUser, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_APP_USER")));
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    } finally {
                        TenantContext.clear();
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isActive(Long userId) {
        AppUser user = userId == null ? null : userMapper.selectById(userId);
        if (user != null && "0".equals(user.getStatus())) {
            return true;
        }
        // 停用/删除后立即失效，避免已有 token 继续访问小程序接口
        tokenService.evictUser(userId);
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (StringUtils.hasText(header)) {
            return header;
        }
        return request.getHeader("App-Token");
    }
}
