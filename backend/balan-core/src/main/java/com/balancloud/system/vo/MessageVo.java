package com.balancloud.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVo {

    private Long id;

    private String msgType;

    private String severity;

    private Long stationId;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    private boolean read;

    private LocalDateTime createTime;
}
