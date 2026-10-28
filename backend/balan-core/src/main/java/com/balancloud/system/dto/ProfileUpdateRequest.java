package com.balancloud.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号自助改资料，只开放 sys_user 里真正存在的三列；角色、状态与场站范围仍只能在用户管理里由管理员改。
 */
@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名长度不能超过 30")
    private String nickName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;

    @Pattern(regexp = "^$|^\\d{6,20}$", message = "手机号格式不正确")
    private String phoneNumber;
}
