package com.balancloud.system.controller;

import com.balancloud.common.core.AjaxResult;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.system.service.MessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/messages")
public class SysMessageController {

    private final MessageService messageService;

    public SysMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public TableDataInfo list(@RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize,
                              @RequestParam(required = false) String msgType,
                              @RequestParam(required = false) String severity) {
        return messageService.list(pageNum, pageSize, msgType, severity);
    }

    @GetMapping("/unread-count")
    public AjaxResult unreadCount() {
        return AjaxResult.success(Map.of("count", messageService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public AjaxResult markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return AjaxResult.success();
    }

    @PostMapping("/read-all")
    public AjaxResult markAllRead() {
        return AjaxResult.success(Map.of("count", messageService.markAllRead()));
    }
}
