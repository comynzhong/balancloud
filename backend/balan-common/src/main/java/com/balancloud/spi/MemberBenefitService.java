package com.balancloud.spi;

import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.spi.dto.BenefitQuery;

import java.util.List;

/**
 * 会员权益扩展点：core 在下单构建费率快照时调用，社区版 no-op，标准版返回会员价/优惠券/活动。
 */
public interface MemberBenefitService {

    List<BenefitItem> evaluate(BenefitQuery query);
}
