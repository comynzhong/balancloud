package com.balancloud.app.dto;

import lombok.Data;

@Data
public class VehicleQuery {

    private String keyword;

    private Long appUserId;

    private long pageNum = 1;

    private long pageSize = 10;
}
