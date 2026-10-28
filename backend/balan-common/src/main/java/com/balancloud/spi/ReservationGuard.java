package com.balancloud.spi;

import com.balancloud.spi.dto.StartGuardQuery;

/**
 * 启动充电前置校验扩展点：社区版放行，标准版实现预约锁定与冲突校验。
 */
public interface ReservationGuard {

    /** 不允许启动时抛 ServiceException，core 按普通业务失败处理。 */
    void assertAllowed(StartGuardQuery query);
}
