package com.balancloud.app.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlacklistVo {

    private Long id;
    private String scopeType;
    private Long appUserId;
    private String nickname;
    private String phone;
    private String plateNo;
    private String reason;
    private String status;
    private LocalDateTime expireAt;
    private boolean expired;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
