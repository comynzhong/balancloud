package com.balancloud.system.vo;

import lombok.Data;

@Data
public class ProfileVo {

    private SystemUserVo user;

    private String tenantName;
}
