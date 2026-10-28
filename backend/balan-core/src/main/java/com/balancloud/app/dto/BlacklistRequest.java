package com.balancloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlacklistRequest {

    /** USER 用户维度 / PLATE 车牌维度。 */
    @NotBlank(message = "黑名单维度不能为空")
    private String scopeType;

    private Long appUserId;

    @Size(max = 20, message = "车牌号不能超过 20 个字符")
    private String plateNo;

    @NotBlank(message = "拉黑原因不能为空")
    @Size(max = 200, message = "拉黑原因不能超过 200 个字符")
    private String reason;

    private LocalDateTime expireAt;
}
