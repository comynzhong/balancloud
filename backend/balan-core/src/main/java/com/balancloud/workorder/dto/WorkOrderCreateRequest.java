package com.balancloud.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderCreateRequest {

    @NotNull(message = "请选择归属场站")
    private Long stationId;

    private Long chargerId;

    private Integer portNo;

    @NotBlank(message = "请填写工单标题")
    @Size(max = 120, message = "标题不能超过 120 字")
    private String title;

    @Size(max = 1000, message = "问题描述不能超过 1000 字")
    private String description;

    private String priority;

    private Long assigneeId;
}
