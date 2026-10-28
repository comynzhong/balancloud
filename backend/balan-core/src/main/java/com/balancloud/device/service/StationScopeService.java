package com.balancloud.device.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.system.mapper.SysUserStationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationScopeService {

    private static final String ADMIN_ROLE = "admin";

    private final SysUserStationMapper userStationMapper;

    public StationScopeService(SysUserStationMapper userStationMapper) {
        this.userStationMapper = userStationMapper;
    }

    /**
     * 返回 null 表示不限制（超级管理员），否则返回当前账号授权的场站 ID。
     */
    public List<Long> authorizedStationIds() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "认证失败，无法访问系统资源");
        }
        if (loginUser.getRoles().contains(ADMIN_ROLE)) {
            return null;
        }
        List<Long> stationIds = userStationMapper.selectStationIdsByUserId(loginUser.getUserId());
        return stationIds == null ? List.of() : stationIds;
    }

    public void assertAccess(Long stationId) {
        List<Long> stationIds = authorizedStationIds();
        if (stationIds != null && (stationId == null || !stationIds.contains(stationId))) {
            throw new ServiceException(403, "无权访问该场站");
        }
    }
}
