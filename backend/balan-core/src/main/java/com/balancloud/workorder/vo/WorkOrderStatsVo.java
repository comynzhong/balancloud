package com.balancloud.workorder.vo;

import lombok.Data;

@Data
public class WorkOrderStatsVo {

    private long total;
    private long open;
    private long processing;
    private long resolved;
    private long closed;
    private long active;
}
