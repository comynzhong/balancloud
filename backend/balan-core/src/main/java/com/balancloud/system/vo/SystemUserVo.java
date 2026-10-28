package com.balancloud.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SystemUserVo {

    private Long userId;

    private String userName;

    private String nickName;

    private String email;

    private String phoneNumber;

    private String status;

    private String remark;

    private String loginIp;

    private LocalDateTime loginDate;

    private LocalDateTime pwdUpdateDate;

    private String pwdResetRequired;

    private LocalDateTime createTime;

    private List<Long> roleIds;

    private List<String> roleNames;

    private List<Long> stationIds;
}
