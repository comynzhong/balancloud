package com.balancloud.system.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.system.captcha.SliderCaptchaService;
import com.balancloud.system.dto.SliderVerifyBody;
import com.balancloud.system.vo.SliderVerifyResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/captcha/slider")
public class SliderCaptchaController {

    private final SliderCaptchaService captchaService;

    public SliderCaptchaController(SliderCaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping
    public AjaxResult create() {
        return AjaxResult.success(captchaService.create());
    }

    @PostMapping("/verify")
    public AjaxResult verify(@Valid @RequestBody SliderVerifyBody body) {
        SliderVerifyResult result = captchaService.verify(body.getId(), body.getX());
        return AjaxResult.success()
                .put("verified", result.isVerified())
                .put("token", result.getToken())
                .put("expired", result.isExpired())
                .put("remaining", result.getRemaining())
                .put("msg", result.getMessage());
    }
}
