package com.balancloud.spi.defaults;

import com.balancloud.spi.SettlementService;
import com.balancloud.spi.dto.SettlementContext;

/**
 * 社区版结算钩子：全额归本租户，不做分账。
 */
public class NoopSettlementService implements SettlementService {

    @Override
    public void afterSettle(SettlementContext context) {
    }
}
