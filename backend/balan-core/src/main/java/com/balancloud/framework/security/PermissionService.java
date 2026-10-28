package com.balancloud.framework.security;

import com.balancloud.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component("ss")
public class PermissionService {

    private static final String ALL_PERMISSION = "*:*:*";

    public boolean hasPermi(String permission) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || permission == null || permission.isBlank()) {
            return false;
        }
        return loginUser.getPermissions().contains(ALL_PERMISSION)
                || loginUser.getPermissions().contains(permission);
    }

    public boolean hasAnyPermi(String... permissions) {
        return Arrays.stream(permissions).anyMatch(this::hasPermi);
    }

    public boolean hasRole(String role) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null && loginUser.getRoles().contains(role);
    }
}
