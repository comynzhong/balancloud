package com.balancloud.order.model;

import com.balancloud.spi.dto.BenefitItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
public class RateSnapshot {

    private Long strategyId;

    private String strategyName;

    private String billingMode;

    private List<RatePeriodSnapshot> periods;

    /** 按生效日期排布的多个方案；为空时使用上面的单策略字段（兼容历史订单）。 */
    private List<RatePlanSnapshot> plans;

    /** MemberBenefitService 产出的权益明细；社区版恒为空，所以不写入 JSON。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<BenefitItem> benefits;
}
