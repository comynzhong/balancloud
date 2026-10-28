package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    private String userName;

    private String nickName;

    private String password;

    private String email;

    private String phoneNumber;

    private String avatar;

    private String status;

    @TableLogic
    private String delFlag;

    private String loginIp;

    private LocalDateTime loginDate;

    private LocalDateTime pwdUpdateDate;

    private String pwdResetRequired;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;
}
