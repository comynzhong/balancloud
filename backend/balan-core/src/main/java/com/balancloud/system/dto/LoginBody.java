package com.balancloud.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginBody {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String code;

    private String uuid;

    /** 滑块验证通过后下发的一次性令牌 */
    private String captchaToken;
}
