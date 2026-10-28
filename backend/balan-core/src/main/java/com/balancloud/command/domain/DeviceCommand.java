package com.balancloud.command.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_command")
public class DeviceCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cmdId;

    private Long chargerId;

    private Integer portNo;

    private String type;

    private String payloadJson;

    private String status;

    private Integer retryCount;

    private LocalDateTime sentAt;

    private LocalDateTime ackedAt;

    private String resultMessage;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String delFlag;
}
