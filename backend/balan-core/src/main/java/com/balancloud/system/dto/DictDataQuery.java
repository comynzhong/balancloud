package com.balancloud.system.dto;

import lombok.Data;

@Data
public class DictDataQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String dictType;
    private String keyword;
    private String status;
}
