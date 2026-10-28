package com.balancloud.rate.vo;

import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.domain.RateStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 一个生效方案：电价策略 + 可选服务费覆盖策略 + 合并后的时段价格。 */
@Data
@AllArgsConstructor
public class RatePlan {

    private RateStrategy strategy;

    private RateStrategy serviceStrategy;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private List<RatePeriod> periods;
}
