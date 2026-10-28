package com.balancloud.app.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user_blacklist")
public class AppBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scopeType;

    private Long appUserId;

    private String plateNo;

    private String reason;

    private String status;

    private LocalDateTime expireAt;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
