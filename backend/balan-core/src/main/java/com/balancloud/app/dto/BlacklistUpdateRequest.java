package com.balancloud.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlacklistUpdateRequest {

    @Size(max = 200, message = "拉黑原因不能超过 200 个字符")
    private String reason;

    private LocalDateTime expireAt;

    /** 0 生效 / 1 已解除；为空表示不变。 */
    private String status;
}
