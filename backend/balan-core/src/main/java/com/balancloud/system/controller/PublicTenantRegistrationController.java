package com.balancloud.system.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.spi.TenantRegistrationService;
import com.balancloud.spi.dto.RegistrationApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营商入驻入口。社区版固定返回未开放，标准版 jar 覆盖 TenantRegistrationService 后提供自助入驻与审核。
 */
@RestController
@RequestMapping("/api/v1/public/tenant-registration")
public class PublicTenantRegistrationController {

    private final TenantRegistrationService registrationService;

    public PublicTenantRegistrationController(TenantRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public AjaxResult status() {
        return AjaxResult.success(Map.of("enabled", registrationService.isEnabled()));
    }

    @PostMapping
    public AjaxResult apply(@RequestBody(required = false) RegistrationApplication application) {
        return AjaxResult.success(registrationService.apply(application));
    }
}
