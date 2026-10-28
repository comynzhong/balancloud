package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_message_read")
public class SysMessageRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private Long userId;

    private LocalDateTime readTime;
}
