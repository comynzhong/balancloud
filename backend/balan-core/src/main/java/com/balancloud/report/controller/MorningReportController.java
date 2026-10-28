package com.balancloud.report.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.report.service.MorningReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运营晨报：默认昨天，页面按账号场站权限；手动推送是租户级动作，仅超管。
 */
@RestController
@RequestMapping("/api/v1/report")
public class MorningReportController {

    private final MorningReportService reportService;

    public MorningReportController(MorningReportService reportService) {
        this.reportService = reportService;
    }

    @PreAuthorize("@ss.hasPermi('report:morning:list')")
    @GetMapping("/morning")
    public AjaxResult morning(@RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return AjaxResult.success(reportService.report(date));
    }

    @PreAuthorize("@ss.hasPermi('report:morning:push')")
    @PostMapping("/morning/push")
    public AjaxResult push(@RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return AjaxResult.success(reportService.push(date));
    }
}
