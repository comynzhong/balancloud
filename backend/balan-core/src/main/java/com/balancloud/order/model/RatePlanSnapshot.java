package com.balancloud.order.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 订单费率快照中的一个生效方案：一段日期区间内的电价策略与服务费覆盖策略。 */
@Data
public class RatePlanSnapshot {

    private Long strategyId;

    private String strategyName;

    private Long serviceStrategyId;

    private String serviceStrategyName;

    private String billingMode;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private List<RatePeriodSnapshot> periods;
}
