package com.balancloud.workorder.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.workorder.dto.WorkOrderAssignRequest;
import com.balancloud.workorder.dto.WorkOrderCloseRequest;
import com.balancloud.workorder.dto.WorkOrderCreateRequest;
import com.balancloud.workorder.dto.WorkOrderFromAlertRequest;
import com.balancloud.workorder.dto.WorkOrderQuery;
import com.balancloud.workorder.dto.WorkOrderRecordRequest;
import com.balancloud.workorder.dto.WorkOrderResolveRequest;
import com.balancloud.workorder.dto.WorkOrderUpdateRequest;
import com.balancloud.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PreAuthorize("@ss.hasPermi('workorder:list')")
    @GetMapping
    public TableDataInfo list(WorkOrderQuery query) {
        return workOrderService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('workorder:list')")
    @GetMapping("/stats")
    public AjaxResult stats() {
        return AjaxResult.success(workOrderService.stats());
    }

    @PreAuthorize("@ss.hasPermi('workorder:assign')")
    @GetMapping("/assignees")
    public AjaxResult assignees() {
        return AjaxResult.success(workOrderService.assignees());
    }

    @PreAuthorize("@ss.hasPermi('workorder:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(workOrderService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('workorder:add')")
    @Log(title = "运维工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@Valid @RequestBody WorkOrderCreateRequest request) {
        return AjaxResult.success(workOrderService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:add')")
    @Log(title = "运维工单", businessType = BusinessType.INSERT)
    @PostMapping("/from-alert/{alertId}")
    public AjaxResult createFromAlert(@PathVariable Long alertId,
                                      @Valid @RequestBody(required = false) WorkOrderFromAlertRequest request) {
        return AjaxResult.success(workOrderService.createFromAlert(alertId, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:process')")
    @Log(title = "运维工单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody WorkOrderUpdateRequest request) {
        return AjaxResult.success(workOrderService.update(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:assign')")
    @Log(title = "运维工单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/assign")
    public AjaxResult assign(@PathVariable Long id, @Valid @RequestBody WorkOrderAssignRequest request) {
        return AjaxResult.success(workOrderService.assign(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:process')")
    @Log(title = "运维工单", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/records")
    public AjaxResult addRecord(@PathVariable Long id, @Valid @RequestBody WorkOrderRecordRequest request) {
        return AjaxResult.success(workOrderService.addRecord(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:process')")
    @Log(title = "运维工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/resolve")
    public AjaxResult resolve(@PathVariable Long id, @Valid @RequestBody WorkOrderResolveRequest request) {
        return AjaxResult.success(workOrderService.resolve(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:process')")
    @Log(title = "运维工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reopen")
    public AjaxResult reopen(@PathVariable Long id,
                             @Valid @RequestBody(required = false) WorkOrderCloseRequest request) {
        return AjaxResult.success(workOrderService.reopen(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:close')")
    @Log(title = "运维工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/close")
    public AjaxResult close(@PathVariable Long id,
                            @Valid @RequestBody(required = false) WorkOrderCloseRequest request) {
        return AjaxResult.success(workOrderService.close(id, request));
    }

    @PreAuthorize("@ss.hasPermi('workorder:remove')")
    @Log(title = "运维工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return AjaxResult.success();
    }
}
