package com.balancloud.system.dto;

import lombok.Data;

@Data
public class DictTypeRequest {

    private String dictName;
    private String dictType;
    private String status;
    private String remark;
}
