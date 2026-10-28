package com.balancloud.rate.vo;

import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.domain.RateStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class EnabledRate {

    /** 当前生效的电价策略。 */
    private RateStrategy strategy;

    /** 当前生效日期下合并服务费后的时段价格。 */
    private List<RatePeriod> periods;

    /** 当前生效的服务费策略，未配置时为 null。 */
    private RateStrategy serviceStrategy;

    /** 订单存续期间可能用到的全部生效方案（按日期区间）。 */
    private List<RatePlan> plans;
}
