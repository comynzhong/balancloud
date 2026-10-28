package com.balancloud.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private String configKey;

    private String configName;

    private String configValue;

    private String valueType;

    private String configGroup;

    private Integer sort;

    private String optionsJson;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private Integer maxLength;

    private String editable;

    private String isSystem;

    private String status;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
