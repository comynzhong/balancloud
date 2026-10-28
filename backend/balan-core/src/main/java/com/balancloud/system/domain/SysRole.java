package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    private String roleName;

    private String roleKey;

    /** 种子角色标记：禁止删除，roleKey 只读（LoginUser.roles 与 @ss.hasRole 按 key 判定）。 */
    private String builtin;

    private Integer roleSort;

    private String status;

    @TableLogic
    private String delFlag;

    private LocalDateTime createTime;

    private String remark;
}
