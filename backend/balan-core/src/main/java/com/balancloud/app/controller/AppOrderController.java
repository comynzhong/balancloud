package com.balancloud.app.controller;

import com.balancloud.app.service.AppOrderService;
import com.balancloud.common.core.AjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/orders")
public class AppOrderController {

    private final AppOrderService orderService;

    public AppOrderController(AppOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public AjaxResult list(@RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "1") long pageNum,
                           @RequestParam(defaultValue = "10") long pageSize) {
        return AjaxResult.success(orderService.list(status, pageNum, pageSize));
    }

    @GetMapping("/active")
    public AjaxResult active() {
        return AjaxResult.success().put("data", orderService.active());
    }

    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        return AjaxResult.success(orderService.detail(id));
    }

    @PostMapping("/{id}/pay")
    public AjaxResult pay(@PathVariable Long id) {
        return AjaxResult.success(orderService.pay(id));
    }
}
