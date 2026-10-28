package com.balancloud.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVo {

    private Long userId;

    private String userName;

    private String nickName;

    private String email;

    private String phoneNumber;

    private String avatar;

    private String status;

    private LocalDateTime createTime;
}
