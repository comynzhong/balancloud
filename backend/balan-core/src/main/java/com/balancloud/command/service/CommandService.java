package com.balancloud.command.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.command.domain.DeviceCommand;
import com.balancloud.command.event.CommandResultEvent;
import com.balancloud.command.mapper.DeviceCommandMapper;
import com.balancloud.command.model.CommandStatus;
import com.balancloud.command.model.CommandType;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.protocol.BalanMqttAdapter;
import com.balancloud.device.protocol.DeviceProtocolAdapter;
import com.balancloud.device.protocol.DownlinkDispatcher;
import com.balancloud.device.protocol.PlatformCommand;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final long ACK_TIMEOUT_SECONDS = 5;
    private static final int MAX_RETRIES = 2;
    private static final long SWEEP_PERIOD_MS = 2000;

    private final DeviceCommandMapper commandMapper;
    private final ChargerMapper chargerMapper;
    private final StationScopeService scopeService;
    private final DownlinkDispatcher downlinkDispatcher;
    private final ProtocolAdapterRegistry registry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantTaskExecutor tenantTasks;

    public CommandService(DeviceCommandMapper commandMapper,
                          ChargerMapper chargerMapper,
                          StationScopeService scopeService,
                          DownlinkDispatcher downlinkDispatcher,
                          ProtocolAdapterRegistry registry,
                          ObjectMapper objectMapper,
                          ApplicationEventPublisher eventPublisher,
                          TenantTaskExecutor tenantTasks) {
        this.commandMapper = commandMapper;
        this.chargerMapper = chargerMapper;
        this.scopeService = scopeService;
        this.downlinkDispatcher = downlinkDispatcher;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.tenantTasks = tenantTasks;
    }

    public DeviceCommand create(Long chargerId, Integer portNo, CommandType type,
                                Map<String, Object> params, String createBy) {
        DeviceCommand command = new DeviceCommand();
        command.setCmdId(UUID.randomUUID().toString());
        command.setChargerId(chargerId);
        command.setPortNo(portNo);
        command.setType(type.name());
        try {
            command.setPayloadJson(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            throw new ServiceException(500, "指令参数序列化失败");
        }
        command.setStatus(CommandStatus.SENT.name());
        command.setRetryCount(0);
        command.setSentAt(LocalDateTime.now());
        command.setCreateBy(createBy);
        commandMapper.insert(command);
        publishAfterCommit(command);
        return command;
    }

    public DeviceCommand detail(String cmdId) {
        DeviceCommand command = findByCmdId(cmdId);
        if (command == null) {
            throw new ServiceException(404, "指令不存在");
        }
        Charger charger = chargerMapper.selectById(command.getChargerId());
        if (charger == null) {
            throw new ServiceException(404, "指令关联设备不存在");
        }
        scopeService.assertAccess(charger.getStationId());
        return command;
    }

    public DeviceCommand findByCmdId(String cmdId) {
        if (!StringUtils.hasText(cmdId)) {
            return null;
        }
        return commandMapper.selectOne(new LambdaQueryWrapper<DeviceCommand>()
                .eq(DeviceCommand::getCmdId, cmdId)
                .last("LIMIT 1"));
    }

    public void handleAck(String deviceNo, PlatformEvent.CommandAck ack) {
        String cmdId = ack.cmdId();
        if (!StringUtils.hasText(cmdId)) {
            log.warn("cmd_ack without cmdId from {}", deviceNo);
            return;
        }
        DeviceCommand command = findByCmdId(cmdId);
        if (command == null) {
            log.warn("cmd_ack for unknown command {} from {}", cmdId, deviceNo);
            return;
        }
        Charger charger = chargerMapper.selectById(command.getChargerId());
        if (charger == null || !charger.getDeviceNo().equals(deviceNo)) {
            log.warn("cmd_ack device mismatch: command {} expects {}, got {}", cmdId,
                    charger == null ? "?" : charger.getDeviceNo(), deviceNo);
            return;
        }
        if (!CommandStatus.SENT.name().equals(command.getStatus())) {
            return;
        }
        String result = ack.rawResult();
        String message = ack.message();
        boolean success = ack.success();
        if (!StringUtils.hasText(message)) {
            message = success ? "设备已回执" : "设备执行失败: " + result;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = commandMapper.update(null, new LambdaUpdateWrapper<DeviceCommand>()
                .eq(DeviceCommand::getId, command.getId())
                .eq(DeviceCommand::getStatus, CommandStatus.SENT.name())
                .set(DeviceCommand::getStatus, success ? CommandStatus.ACKED.name() : CommandStatus.FAILED.name())
                .set(DeviceCommand::getAckedAt, now)
                .set(DeviceCommand::getResultMessage, truncate(message))
                .set(DeviceCommand::getUpdateBy, "device"));
        if (updated == 0) {
            return;
        }
        command.setStatus(success ? CommandStatus.ACKED.name() : CommandStatus.FAILED.name());
        command.setAckedAt(now);
        command.setResultMessage(truncate(message));
        eventPublisher.publishEvent(new CommandResultEvent(command, success ? "SUCCESS" : "FAILED"));
    }

    @Scheduled(fixedDelay = SWEEP_PERIOD_MS, initialDelay = 5000)
    public void sweepTimeouts() {
        tenantTasks.forEach("command-retry", Duration.ofMillis(SWEEP_PERIOD_MS),
                Duration.ofMillis(SWEEP_PERIOD_MS - 500), this::sweepTimeoutsForTenant);
    }

    private void sweepTimeoutsForTenant() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(ACK_TIMEOUT_SECONDS);
        List<DeviceCommand> pending = commandMapper.selectList(new LambdaQueryWrapper<DeviceCommand>()
                .eq(DeviceCommand::getStatus, CommandStatus.SENT.name())
                .lt(DeviceCommand::getSentAt, threshold)
                .orderByAsc(DeviceCommand::getSentAt)
                .last("LIMIT 50"));
        for (DeviceCommand command : pending) {
            LocalDateTime now = LocalDateTime.now();
            if (command.getRetryCount() == null || command.getRetryCount() < MAX_RETRIES) {
                int currentRetry = command.getRetryCount() == null ? 0 : command.getRetryCount();
                int updated = commandMapper.update(null, new LambdaUpdateWrapper<DeviceCommand>()
                        .eq(DeviceCommand::getId, command.getId())
                        .eq(DeviceCommand::getStatus, CommandStatus.SENT.name())
                        .eq(DeviceCommand::getRetryCount, currentRetry)
                        .set(DeviceCommand::getRetryCount, currentRetry + 1)
                        .set(DeviceCommand::getSentAt, now));
                if (updated == 1) {
                    command.setRetryCount(currentRetry + 1);
                    command.setSentAt(now);
                    log.info("Retry command {} ({}) attempt {}", command.getCmdId(), command.getType(),
                            currentRetry + 1);
                    deliver(command);
                }
                continue;
            }
            int updated = commandMapper.update(null, new LambdaUpdateWrapper<DeviceCommand>()
                    .eq(DeviceCommand::getId, command.getId())
                    .eq(DeviceCommand::getStatus, CommandStatus.SENT.name())
                    .set(DeviceCommand::getStatus, CommandStatus.TIMEOUT.name())
                    .set(DeviceCommand::getAckedAt, now)
                    .set(DeviceCommand::getResultMessage, "设备未在 5 秒内回执，已重试 2 次")
                    .set(DeviceCommand::getUpdateBy, "system"));
            if (updated == 1) {
                command.setStatus(CommandStatus.TIMEOUT.name());
                command.setAckedAt(now);
                command.setResultMessage("设备未在 5 秒内回执，已重试 2 次");
                log.warn("Command {} ({}) timed out", command.getCmdId(), command.getType());
                eventPublisher.publishEvent(new CommandResultEvent(command, "TIMEOUT"));
            }
        }
    }

    private void publishAfterCommit(DeviceCommand command) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(command);
                }
            });
        } else {
            deliver(command);
        }
    }

    private void deliver(DeviceCommand command) {
        Charger charger = chargerMapper.selectById(command.getChargerId());
        if (charger == null || !StringUtils.hasText(charger.getDeviceNo())) {
            log.warn("Cannot deliver command {}: charger {} missing", command.getCmdId(), command.getChargerId());
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(command.getPayloadJson())) {
            try {
                JsonNode node = objectMapper.readTree(command.getPayloadJson());
                params = objectMapper.convertValue(node, Map.class);
            } catch (Exception e) {
                log.warn("Invalid command payload {}: {}", command.getCmdId(), e.getMessage());
            }
        }
        String protocol = StringUtils.hasText(charger.getProtocol())
                ? charger.getProtocol().trim().toUpperCase() : BalanMqttAdapter.PROTOCOL;
        DeviceProtocolAdapter adapter;
        try {
            adapter = registry.require(protocol);
        } catch (IllegalStateException e) {
            log.warn("Cannot deliver command {} to {}: {}", command.getCmdId(), charger.getDeviceNo(), e.getMessage());
            return;
        }
        CommandType type = null;
        try {
            type = CommandType.valueOf(command.getType());
        } catch (Exception e) {
            log.warn("Command {} has unknown type {}", command.getCmdId(), command.getType());
        }
        PlatformCommand platformCommand = PlatformCommand.control(
                command.getCmdId(), charger.getDeviceNo(), command.getPortNo(), type, params);
        if (!downlinkDispatcher.send(adapter.encode(platformCommand))) {
            log.warn("Downlink unavailable, command {} will be retried", command.getCmdId());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
