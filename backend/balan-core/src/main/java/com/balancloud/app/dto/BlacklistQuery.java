package com.balancloud.app.dto;

import lombok.Data;

@Data
public class BlacklistQuery {

    private String keyword;

    /** USER / PLATE，空表示全部。 */
    private String scopeType;

    /** 0 生效 / 1 已解除，空表示全部。 */
    private String status;

    private long pageNum = 1;

    private long pageSize = 10;
}
