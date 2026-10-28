package com.balancloud.system.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.service.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录前与小程序启动时读取的公开配置，只包含品牌、维护公告与展示性开关。
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicConfigController {

    private final SystemConfigService configService;

    public PublicConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    public AjaxResult config() {
        return AjaxResult.success(configService.publicConfig());
    }
}
