package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config_group")
public class SysConfigGroup {

    @TableId(value = "group_key", type = IdType.INPUT)
    private String groupKey;

    private String groupName;

    private Integer sort;

    private String builtin;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
