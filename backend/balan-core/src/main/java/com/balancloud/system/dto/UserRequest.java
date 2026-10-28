package com.balancloud.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserRequest {

    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{2,30}$", message = "登录名只允许字母、数字、下划线，长度 2-30")
    private String userName;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名长度不能超过 30")
    private String nickName;

    @Size(min = 6, max = 30, message = "密码长度需为 6-30 位")
    private String password;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;

    @Pattern(regexp = "^$|^\\d{6,20}$", message = "手机号格式不正确")
    private String phoneNumber;

    @Pattern(regexp = "0|1", message = "账号状态不合法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    private List<Long> roleIds;

    private List<Long> stationIds;
}
