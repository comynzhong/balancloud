package com.balancloud.command.controller;

import com.balancloud.command.service.CommandService;
import com.balancloud.common.core.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PreAuthorize("@ss.hasPermi('charger:query')")
    @GetMapping("/{cmdId}")
    public AjaxResult detail(@PathVariable String cmdId) {
        return AjaxResult.success(commandService.detail(cmdId));
    }
}
