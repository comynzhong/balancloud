package com.balancloud.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NameCountVo {

    private String code;

    private String label;

    private long count;
}
