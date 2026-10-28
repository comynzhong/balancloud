package com.balancloud.system.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ConfigItemVo {

    private String configKey;

    private String configName;

    private String configValue;

    private String valueType;

    private List<ConfigOptionVo> options;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private Integer maxLength;

    private boolean editable;

    private boolean builtin;

    private String remark;
}
