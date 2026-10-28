package com.balancloud.spi.defaults;

import com.balancloud.spi.ReservationGuard;
import com.balancloud.spi.dto.StartGuardQuery;

/**
 * 社区版无预约能力，启动充电一律放行。
 */
public class PermitAllReservationGuard implements ReservationGuard {

    @Override
    public void assertAllowed(StartGuardQuery query) {
    }
}
