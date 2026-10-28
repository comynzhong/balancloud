package com.balancloud.system.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.system.dto.LogininforQuery;
import com.balancloud.system.dto.OperLogQuery;
import com.balancloud.system.service.OperLogQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/logs")
public class SystemLogController {

    private final OperLogQueryService operLogQueryService;

    public SystemLogController(OperLogQueryService operLogQueryService) {
        this.operLogQueryService = operLogQueryService;
    }

    @PreAuthorize("@ss.hasPermi('system:log:list')")
    @GetMapping("/oper")
    public TableDataInfo listOper(OperLogQuery query) {
        return operLogQueryService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('system:log:list')")
    @GetMapping("/oper/titles")
    public AjaxResult titles() {
        return AjaxResult.success(operLogQueryService.titles());
    }

    @PreAuthorize("@ss.hasPermi('system:log:query')")
    @GetMapping("/oper/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(operLogQueryService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('system:log:export')")
    @Log(title = "审计日志", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/oper/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void export(OperLogQuery query, HttpServletResponse response) {
        operLogQueryService.export(query, response);
    }

    @PreAuthorize("@ss.hasPermi('system:log:list')")
    @GetMapping("/login")
    public TableDataInfo listLogin(LogininforQuery query) {
        return operLogQueryService.listLoginLog(query);
    }
}
