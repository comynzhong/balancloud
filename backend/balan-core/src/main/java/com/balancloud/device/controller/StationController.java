package com.balancloud.device.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.device.dto.StationRequest;
import com.balancloud.device.service.StationService;
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
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PreAuthorize("@ss.hasPermi('station:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String name,
                              @RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize) {
        return stationService.list(name, status, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('station:query')")
    @GetMapping("/options")
    public AjaxResult options() {
        return AjaxResult.success(stationService.options());
    }

    @PreAuthorize("@ss.hasPermi('station:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(stationService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('station:add')")
    @Log(title = "场站管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody StationRequest request) {
        return AjaxResult.success(stationService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('station:edit')")
    @Log(title = "场站管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody StationRequest request) {
        return AjaxResult.success(stationService.update(id, request));
    }

    @PreAuthorize("@ss.hasPermi('station:remove')")
    @Log(title = "场站管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        stationService.delete(id);
        return AjaxResult.success();
    }
}
