package com.balancloud.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordRequest {

    @NotNull(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需为 6-30 位")
    @Pattern(regexp = "^[^\\s]{6,30}$", message = "密码不能包含空格")
    private String password;
}
