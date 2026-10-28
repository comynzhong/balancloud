package com.balancloud.device.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.device.dto.ChargerRequest;
import com.balancloud.device.service.ChargerService;
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
@RequestMapping("/api/v1/chargers")
public class ChargerController {

    private final ChargerService chargerService;

    public ChargerController(ChargerService chargerService) {
        this.chargerService = chargerService;
    }

    @PreAuthorize("@ss.hasPermi('charger:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) Long stationId,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String onlineStatus,
                              @RequestParam(required = false) String groupName,
                              @RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize) {
        return chargerService.list(stationId, keyword, onlineStatus, groupName, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('charger:list')")
    @GetMapping("/groups")
    public AjaxResult groups() {
        return AjaxResult.success(chargerService.groups());
    }

    @PreAuthorize("@ss.hasPermi('charger:list')")
    @GetMapping("/scenes")
    public AjaxResult scenes() {
        return AjaxResult.success(chargerService.scenes());
    }

    @PreAuthorize("@ss.hasPermi('charger:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(chargerService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('charger:query')")
    @GetMapping("/{id}/ports")
    public AjaxResult ports(@PathVariable Long id) {
        return AjaxResult.success(chargerService.ports(id));
    }

    @PreAuthorize("@ss.hasPermi('charger:query')")
    @GetMapping("/{id}/history")
    public AjaxResult history(@PathVariable Long id,
                              @RequestParam Integer portNo,
                              @RequestParam(defaultValue = "30") int minutes,
                              @RequestParam(defaultValue = "1000") int limit) {
        return AjaxResult.success(chargerService.history(id, portNo, minutes, limit));
    }

    @PreAuthorize("@ss.hasPermi('charger:add')")
    @Log(title = "充电设备", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody ChargerRequest request) {
        return AjaxResult.success(chargerService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('charger:edit')")
    @Log(title = "充电设备", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody ChargerRequest request) {
        return AjaxResult.success(chargerService.update(id, request));
    }

    @PreAuthorize("@ss.hasPermi('charger:remove')")
    @Log(title = "充电设备", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        chargerService.delete(id);
        return AjaxResult.success();
    }
}
