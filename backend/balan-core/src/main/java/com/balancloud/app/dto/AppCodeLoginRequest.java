package com.balancloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppCodeLoginRequest {

    @NotBlank(message = "登录凭证不能为空")
    @Size(max = 128, message = "登录凭证不合法")
    private String code;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
