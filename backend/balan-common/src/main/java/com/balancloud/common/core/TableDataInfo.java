package com.balancloud.common.core;

import java.util.List;

public class TableDataInfo {

    private final int code = 200;
    private final String msg = "查询成功";
    private final long total;
    private final List<?> rows;

    private TableDataInfo(long total, List<?> rows) {
        this.total = total;
        this.rows = rows;
    }

    public static TableDataInfo of(long total, List<?> rows) {
        return new TableDataInfo(total, rows);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public long getTotal() {
        return total;
    }

    public List<?> getRows() {
        return rows;
    }
}
