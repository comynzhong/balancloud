package com.balancloud.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RoleRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 30, message = "角色名称长度不能超过 30")
    private String roleName;

    @NotBlank(message = "角色标识不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$", message = "角色标识需以小写字母开头，只允许小写字母、数字、下划线，长度 2-50")
    private String roleKey;

    @NotNull(message = "显示顺序不能为空")
    @Min(value = 0, message = "显示顺序不能为负数")
    private Integer roleSort;

    @Pattern(regexp = "^$|^0$|^1$", message = "角色状态不合法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 为空表示不改动授权；保存授权走 PUT /roles/{id}/menus。 */
    private List<Long> menuIds;
}
