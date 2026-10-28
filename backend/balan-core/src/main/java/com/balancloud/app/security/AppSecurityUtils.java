package com.balancloud.app.security;

import com.balancloud.common.exception.ServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AppSecurityUtils {

    private AppSecurityUtils() {
    }

    public static AppLoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppLoginUser loginUser) {
            return loginUser;
        }
        throw new ServiceException(401, "请先登录");
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }
}
