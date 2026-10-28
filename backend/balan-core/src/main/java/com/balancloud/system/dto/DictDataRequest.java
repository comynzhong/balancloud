package com.balancloud.system.dto;

import lombok.Data;

@Data
public class DictDataRequest {

    private Integer dictSort;
    private String dictLabel;
    private String dictValue;
    private String dictType;
    private String cssClass;
    private String listClass;
    private String isDefault;
    private String status;
    private String remark;
}
