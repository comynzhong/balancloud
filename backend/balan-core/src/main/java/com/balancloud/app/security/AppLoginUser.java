package com.balancloud.app.security;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppLoginUser implements Serializable {

    private Long tenantId;

    private Long userId;

    private String nickname;

    private String phone;

    private String avatar;

    private long loginTime;

    private long expireTime;
}
