package com.balancloud.workorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderAssignRequest {

    @NotNull(message = "请选择处理人")
    private Long assigneeId;
}
