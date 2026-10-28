package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String msgType;

    private String severity;

    private Long stationId;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    private LocalDateTime createTime;
}
