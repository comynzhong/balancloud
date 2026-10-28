package com.balancloud.workorder.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkOrderDetailVo {

    private WorkOrderVo workOrder;
    private List<WorkOrderRecordVo> records;
}
