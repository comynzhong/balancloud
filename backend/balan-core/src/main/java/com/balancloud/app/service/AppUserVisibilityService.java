package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小程序用户不是场站档案，运营人员只能看到在其授权场站产生过订单的用户。
 */
@Service
public class AppUserVisibilityService {

    private final StationScopeService scopeService;
    private final ChargeOrderMapper orderMapper;

    public AppUserVisibilityService(StationScopeService scopeService, ChargeOrderMapper orderMapper) {
        this.scopeService = scopeService;
        this.orderMapper = orderMapper;
    }

    /** null 表示管理员不限制；否则返回可见用户 ID，可能为空。 */
    public List<Long> visibleUserIds() {
        List<Long> stationIds = scopeService.authorizedStationIds();
        if (stationIds == null) {
            return null;
        }
        if (stationIds.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectObjs(new QueryWrapper<ChargeOrder>()
                        .select("DISTINCT app_user_id")
                        .in("station_id", stationIds)
                        .isNotNull("app_user_id"))
                .stream()
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue())
                .toList();
    }

    public void assertVisible(Long userId) {
        List<Long> visible = visibleUserIds();
        if (visible != null && !visible.contains(userId)) {
            throw new com.balancloud.common.exception.ServiceException(403, "无权操作该小程序用户");
        }
    }
}
