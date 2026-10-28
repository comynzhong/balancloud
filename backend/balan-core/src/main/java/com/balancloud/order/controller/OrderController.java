package com.balancloud.order.controller;

import com.balancloud.common.annotation.BusinessType;
import com.balancloud.common.annotation.Log;
import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.order.dto.AmountCorrectRequest;
import com.balancloud.order.dto.ManualReasonRequest;
import com.balancloud.order.dto.OrderQuery;
import com.balancloud.order.dto.RefundRequest;
import com.balancloud.order.service.ActiveChargingService;
import com.balancloud.order.service.OrderManualService;
import com.balancloud.order.service.OrderQueryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final ActiveChargingService activeChargingService;
    private final OrderManualService orderManualService;

    public OrderController(OrderQueryService orderQueryService,
                           ActiveChargingService activeChargingService,
                           OrderManualService orderManualService) {
        this.orderQueryService = orderQueryService;
        this.activeChargingService = activeChargingService;
        this.orderManualService = orderManualService;
    }

    @PreAuthorize("@ss.hasPermi('order:list')")
    @GetMapping
    public TableDataInfo list(OrderQuery query) {
        return orderQueryService.list(query);
    }

    @PreAuthorize("@ss.hasPermi('order:list')")
    @GetMapping("/active")
    public AjaxResult active(@RequestParam(required = false) Long stationId) {
        return AjaxResult.success(activeChargingService.list(stationId));
    }

    @PreAuthorize("@ss.hasPermi('order:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(orderQueryService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('order:pay')")
    @Log(title = "充电订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/pay")
    public AjaxResult pay(@PathVariable Long id) {
        return AjaxResult.success(orderQueryService.pay(id));
    }

    @PreAuthorize("@ss.hasPermi('order:refund')")
    @Log(title = "订单退款", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/refund")
    public AjaxResult refund(@PathVariable Long id, @RequestBody(required = false) RefundRequest request) {
        return AjaxResult.success(orderQueryService.refund(id,
                request == null ? null : request.getReason()));
    }

    @PreAuthorize("@ss.hasPermi('order:forceFinish')")
    @Log(title = "订单强制结束", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/force-finish")
    public AjaxResult forceFinish(@PathVariable Long id, @Valid @RequestBody ManualReasonRequest request) {
        orderManualService.forceFinish(id, request.getReason());
        return AjaxResult.success(orderQueryService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('order:correctAmount')")
    @Log(title = "订单金额修正", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/correct-amount")
    public AjaxResult correctAmount(@PathVariable Long id, @Valid @RequestBody AmountCorrectRequest request) {
        orderManualService.correctAmount(id, request);
        return AjaxResult.success(orderQueryService.detail(id));
    }

    @PreAuthorize("@ss.hasPermi('order:query')")
    @GetMapping("/{id}/manual-logs")
    public AjaxResult manualLogs(@PathVariable Long id) {
        return AjaxResult.success(orderManualService.listLogs(id));
    }

    @PreAuthorize("@ss.hasPermi('order:export')")
    @Log(title = "充电订单", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void export(OrderQuery query, HttpServletResponse response) {
        orderQueryService.export(query, response);
    }

    @PreAuthorize("@ss.hasPermi('order:export')")
    @Log(title = "退款记录", businessType = BusinessType.EXPORT)
    @RequestMapping(value = "/refunds/export", method = {RequestMethod.GET, RequestMethod.POST})
    public void exportRefunds(OrderQuery query, HttpServletResponse response) {
        orderQueryService.exportRefunds(query, response);
    }
}
