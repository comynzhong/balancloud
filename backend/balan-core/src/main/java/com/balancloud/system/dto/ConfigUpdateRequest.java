package com.balancloud.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class ConfigUpdateRequest {

    @NotEmpty(message = "配置项不能为空")
    private Map<String, Object> values;
}
