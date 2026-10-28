package com.balancloud.system.dto;

import com.balancloud.system.vo.ConfigOptionVo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 扩展配置项的新建与修改。configKey 只在新建时生效，修改时以路径参数为准。
 */
@Data
public class ConfigItemRequest {

    @Size(max = 100, message = "配置键不能超过 100 个字符")
    private String configKey;

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过 100 个字符")
    private String configName;

    @NotBlank(message = "配置分组不能为空")
    private String configGroup;

    @NotBlank(message = "配置类型不能为空")
    private String valueType;

    private String configValue;

    private List<ConfigOptionVo> options;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private Integer maxLength;

    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
}
