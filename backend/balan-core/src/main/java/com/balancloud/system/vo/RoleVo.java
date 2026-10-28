package com.balancloud.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVo {

    private Long roleId;

    private String roleName;

    private String roleKey;

    /** 是否内置角色：'1' 时前端隐藏删除按钮、roleKey 输入框只读。 */
    private String builtin;

    private Integer roleSort;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    private Integer userCount;

    private Integer menuCount;
}
