package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.spi.NotificationDispatcher;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.spi.dto.NotificationEvent;
import com.balancloud.system.domain.SysMessage;
import com.balancloud.system.domain.SysMessageRead;
import com.balancloud.system.mapper.SysMessageMapper;
import com.balancloud.system.mapper.SysMessageReadMapper;
import com.balancloud.system.vo.MessageVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内消息：告警/订单事件经通知渠道落库，按用户记已读回执，未读数量按场站数据权限过滤。
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final SysMessageMapper messageMapper;
    private final SysMessageReadMapper readMapper;
    private final StationScopeService scopeService;
    private final NotificationDispatcher dispatcher;

    public MessageService(SysMessageMapper messageMapper,
                          SysMessageReadMapper readMapper,
                          StationScopeService scopeService,
                          NotificationDispatcher dispatcher) {
        this.messageMapper = messageMapper;
        this.readMapper = readMapper;
        this.scopeService = scopeService;
        this.dispatcher = dispatcher;
    }

    public void publish(String msgType, String severity, Long stationId,
                        String title, String content, String bizType, Object bizId) {
        if (!StringUtils.hasText(title)) {
            return;
        }
        NotificationEvent event = new NotificationEvent(null,
                StringUtils.hasText(msgType) ? msgType : "SYSTEM",
                StringUtils.hasText(severity) ? severity : "INFO",
                TenantContext.get(), stationId, title, content,
                bizType, bizId == null ? null : String.valueOf(bizId),
                LocalDateTime.now());
        dispatcher.dispatch(event);
    }

    public TableDataInfo list(long pageNum, long pageSize, String msgType, String severity) {
        LambdaQueryWrapper<SysMessage> wrapper = scoped()
                .eq(StringUtils.hasText(msgType), SysMessage::getMsgType, msgType)
                .eq(StringUtils.hasText(severity), SysMessage::getSeverity, severity)
                .orderByDesc(SysMessage::getCreateTime)
                .orderByDesc(SysMessage::getId);
        Page<SysMessage> page = messageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return TableDataInfo.of(page.getTotal(), toVo(page.getRecords(), SecurityUtils.getUserId()));
    }

    public long unreadCount() {
        List<SysMessage> visible = messageMapper.selectList(scoped().select(SysMessage::getId));
        if (visible.isEmpty()) {
            return 0;
        }
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return visible.size();
        }
        Set<Long> ids = visible.stream().map(SysMessage::getId).collect(Collectors.toSet());
        long read = readMapper.selectCount(new LambdaQueryWrapper<SysMessageRead>()
                .eq(SysMessageRead::getUserId, userId)
                .in(SysMessageRead::getMessageId, ids));
        return Math.max(0, visible.size() - read);
    }

    @Transactional
    public void markRead(Long messageId) {
        SysMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new ServiceException(404, "消息不存在");
        }
        ensureRead(messageId, SecurityUtils.getUserId());
    }

    @Transactional
    public int markAllRead() {
        List<SysMessage> visible = messageMapper.selectList(scoped().select(SysMessage::getId));
        if (visible.isEmpty()) {
            return 0;
        }
        Long userId = SecurityUtils.getUserId();
        Set<Long> readIds = readMapper.selectList(new LambdaQueryWrapper<SysMessageRead>()
                        .eq(SysMessageRead::getUserId, userId))
                .stream().map(SysMessageRead::getMessageId).collect(Collectors.toSet());
        int marked = 0;
        for (SysMessage message : visible) {
            if (!readIds.contains(message.getId()) && ensureRead(message.getId(), userId)) {
                marked += 1;
            }
        }
        return marked;
    }

    private boolean ensureRead(Long messageId, Long userId) {
        if (messageId == null || userId == null) {
            return false;
        }
        SysMessageRead receipt = new SysMessageRead();
        receipt.setMessageId(messageId);
        receipt.setUserId(userId);
        receipt.setReadTime(LocalDateTime.now());
        try {
            readMapper.insert(receipt);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to mark message {} read for user {}: {}", messageId, userId, e.getMessage());
            return false;
        }
    }

    private LambdaQueryWrapper<SysMessage> scoped() {
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        List<Long> stations = scopeService.authorizedStationIds();
        if (stations == null) {
            return wrapper;
        }
        if (stations.isEmpty()) {
            return wrapper.isNull(SysMessage::getStationId);
        }
        return wrapper.and(w -> w.isNull(SysMessage::getStationId).or().in(SysMessage::getStationId, stations));
    }

    private List<MessageVo> toVo(List<SysMessage> messages, Long userId) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Set<Long> readIds = readMapper.selectList(new LambdaQueryWrapper<SysMessageRead>()
                        .eq(SysMessageRead::getUserId, userId)
                        .in(SysMessageRead::getMessageId,
                                messages.stream().map(SysMessage::getId).collect(Collectors.toSet())))
                .stream().map(SysMessageRead::getMessageId).collect(Collectors.toSet());
        return messages.stream().map(message -> {
            MessageVo vo = new MessageVo();
            vo.setId(message.getId());
            vo.setMsgType(message.getMsgType());
            vo.setSeverity(message.getSeverity());
            vo.setStationId(message.getStationId());
            vo.setTitle(message.getTitle());
            vo.setContent(message.getContent());
            vo.setBizType(message.getBizType());
            vo.setBizId(message.getBizId());
            vo.setCreateTime(message.getCreateTime());
            vo.setRead(readIds.contains(message.getId()));
            return vo;
        }).toList();
    }
}
