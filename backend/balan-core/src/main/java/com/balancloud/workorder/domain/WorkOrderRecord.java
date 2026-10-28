package com.balancloud.workorder.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_record")
public class WorkOrderRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    private String action;

    private String content;

    private String operator;

    private LocalDateTime createTime;
}
