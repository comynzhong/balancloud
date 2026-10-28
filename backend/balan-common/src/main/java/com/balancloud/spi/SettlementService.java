package com.balancloud.spi;

import com.balancloud.spi.dto.SettlementContext;

/**
 * 订单结算钩子：core 在订单终态落库后调用，社区版 no-op（全额归本租户），标准版做分账/抽佣/账单。
 */
public interface SettlementService {

    void afterSettle(SettlementContext context);
}
