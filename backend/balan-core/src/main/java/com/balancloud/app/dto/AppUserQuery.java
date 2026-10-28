package com.balancloud.app.dto;

import lombok.Data;

@Data
public class AppUserQuery {

    private String keyword;

    private String status;

    private long pageNum = 1;

    private long pageSize = 10;
}
