package com.balancloud.staff.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.PermissionService;
import com.balancloud.staff.service.StaffDeviceCardService;
import com.balancloud.staff.vo.StaffProfileVo;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动运维工作台接口（§50.3）。员工身份就是后台账号，走既有 JWT 链；
 * 这里只有两个只读聚合，停充/解锁/告警处置一律复用原有接口。
 */
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final StaffDeviceCardService deviceCardService;
    private final StationScopeService stationScopeService;
    private final PermissionService permissionService;

    public StaffController(StaffDeviceCardService deviceCardService,
                           StationScopeService stationScopeService,
                           PermissionService permissionService) {
        this.deviceCardService = deviceCardService;
        this.stationScopeService = stationScopeService;
        this.permissionService = permissionService;
    }

    /**
     * 小程序「我的」页用这个接口的状态码决定要不要显示运维入口：
     * 只有后台账号能拿到 200，C 端 App token 到不了这里。
     */
    @GetMapping("/profile")
    public AjaxResult profile() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(401, "请使用运维账号登录");
        }
        List<Long> scope = stationScopeService.authorizedStationIds();
        StaffProfileVo profile = new StaffProfileVo();
        profile.setUsername(loginUser.getUsername());
        profile.setNickName(loginUser.getNickName());
        profile.setRoleKeys(new ArrayList<>(loginUser.getRoles()));
        profile.setStationLimited(scope != null);
        profile.setStationCount(scope == null ? null : scope.size());
        profile.setCanStop(permissionService.hasPermi("charger:stop"));
        profile.setCanUnlock(permissionService.hasPermi("charger:unlock"));
        profile.setCanAckAlert(permissionService.hasPermi("alert:ack"));
        profile.setCanResolveAlert(permissionService.hasPermi("alert:resolve"));
        return AjaxResult.success(profile);
    }

    @PreAuthorize("@ss.hasPermi('charger:list')")
    @GetMapping("/devices/{deviceNumber}")
    public AjaxResult deviceCard(@PathVariable String deviceNumber) {
        return AjaxResult.success(deviceCardService.deviceCard(deviceNumber));
    }
}
