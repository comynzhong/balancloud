package com.balancloud.system.dto;

import lombok.Data;

@Data
public class DictTypeQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String keyword;
    private String status;
}
