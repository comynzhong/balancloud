package com.balancloud.framework.security;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class LoginUser implements Serializable {

    private Long tenantId;

    private Long userId;

    private String username;

    private String nickName;

    private Set<String> roles = new LinkedHashSet<>();

    private Set<String> permissions = new LinkedHashSet<>();

    private Set<Long> stationIds = new LinkedHashSet<>();

    private boolean pwdResetRequired;

    private long loginTime;

    private long expireTime;
}
