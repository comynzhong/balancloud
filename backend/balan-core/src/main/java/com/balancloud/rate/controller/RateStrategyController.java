package com.balancloud.rate.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.rate.dto.RatePeriodsRequest;
import com.balancloud.rate.dto.RateStrategyRequest;
import com.balancloud.rate.service.RateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rate-strategies")
public class RateStrategyController {

    private final RateService rateService;

    public RateStrategyController(RateService rateService) {
        this.rateService = rateService;
    }

    @PreAuthorize("@ss.hasPermi('rate:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) Long stationId,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Integer enabled,
                              @RequestParam(required = false) String strategyType,
                              @RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize) {
        return rateService.list(stationId, keyword, enabled, strategyType, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('rate:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(rateService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('rate:add')")
    @Log(title = "费率策略", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody RateStrategyRequest request) {
        return AjaxResult.success(rateService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('rate:edit')")
    @Log(title = "费率策略", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody RateStrategyRequest request) {
        return AjaxResult.success(rateService.update(id, request));
    }

    @PreAuthorize("@ss.hasPermi('rate:remove')")
    @Log(title = "费率策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        rateService.delete(id);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('rate:enable')")
    @Log(title = "费率策略", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/enable")
    public AjaxResult enable(@PathVariable Long id) {
        return AjaxResult.success(rateService.enable(id));
    }

    @PreAuthorize("@ss.hasPermi('rate:enable')")
    @Log(title = "费率策略", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public AjaxResult disable(@PathVariable Long id) {
        return AjaxResult.success(rateService.disable(id));
    }

    @PreAuthorize("@ss.hasPermi('rate:query')")
    @GetMapping("/{id}/periods")
    public AjaxResult periods(@PathVariable Long id) {
        return AjaxResult.success(rateService.periods(id));
    }

    @PreAuthorize("@ss.hasPermi('rate:edit')")
    @Log(title = "费率时段", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/periods")
    public AjaxResult replacePeriods(@PathVariable Long id, @Valid @RequestBody RatePeriodsRequest request) {
        return AjaxResult.success(rateService.replacePeriods(id, request.getPeriods()));
    }
}
