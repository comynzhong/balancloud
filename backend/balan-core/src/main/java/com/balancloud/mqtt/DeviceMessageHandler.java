package com.balancloud.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.balancloud.alert.model.AlertLevel;
import com.balancloud.alert.model.AlertType;
import com.balancloud.alert.service.AlertService;
import com.balancloud.alert.service.MetricAlertEvaluator;
import com.balancloud.command.service.CommandService;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.ocpp.service.OcppOrderBridge;
import com.balancloud.device.protocol.PlatformEvent;
import com.balancloud.device.protocol.PortSample;
import com.balancloud.device.protocol.UplinkEvent;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.metric.MetricIngestService;
import com.balancloud.metric.MetricPoint;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.service.ChargingService;
import com.balancloud.order.service.OccupancyService;
import com.balancloud.ws.EventStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceMessageHandler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long OFFLINE_AFTER_SECONDS = 90;
    private static final long OFFLINE_SWEEP_SECONDS = 30;

    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final DeviceShadowService shadowService;
    private final MetricIngestService metricIngestService;
    private final CommandService commandService;
    private final ChargingService chargingService;
    private final OccupancyService occupancyService;
    private final AlertService alertService;
    private final MetricAlertEvaluator alertEvaluator;
    private final EventStreamService eventStreamService;
    private final StringRedisTemplate redisTemplate;
    private final TenantTaskExecutor tenantTasks;
    private final OcppOrderBridge ocppOrderBridge;

    /** 设备重连后要在首帧测点上核对平台订单状态；共享订阅下上线帧与测点可能落在不同实例，标记必须放 Redis。 */
    private static final String RECONCILE_PREFIX = "balan:reconcile:";
    private static final Duration RECONCILE_TTL = Duration.ofMinutes(10);

    public DeviceMessageHandler(ChargerMapper chargerMapper,
                                ChargerPortMapper portMapper,
                                DeviceShadowService shadowService,
                                MetricIngestService metricIngestService,
                                CommandService commandService,
                                ChargingService chargingService,
                                OccupancyService occupancyService,
                                AlertService alertService,
                                MetricAlertEvaluator alertEvaluator,
                                EventStreamService eventStreamService,
                                StringRedisTemplate redisTemplate,
                                TenantTaskExecutor tenantTasks,
                                OcppOrderBridge ocppOrderBridge) {
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.shadowService = shadowService;
        this.metricIngestService = metricIngestService;
        this.commandService = commandService;
        this.chargingService = chargingService;
        this.occupancyService = occupancyService;
        this.alertService = alertService;
        this.alertEvaluator = alertEvaluator;
        this.eventStreamService = eventStreamService;
        this.redisTemplate = redisTemplate;
        this.tenantTasks = tenantTasks;
        this.ocppOrderBridge = ocppOrderBridge;
    }

    @EventListener
    public void onUplink(UplinkEvent event) {
        TenantContext.use(event.tenantId());
        try {
            handle(event.event());
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void handle(PlatformEvent event) {
        String deviceNo = event.deviceNo();
        if (event instanceof PlatformEvent.Unrecognized unrecognized) {
            log.debug("Ignoring unsupported event from {}: {}", deviceNo, unrecognized.reason());
            return;
        }
        Charger charger = chargerMapper.selectByDeviceNo(deviceNo);
        if (charger == null) {
            log.warn("Received {} from unknown device {}", event.getClass().getSimpleName(), deviceNo);
            return;
        }
        if (event instanceof PlatformEvent.Connectivity connectivity) {
            handleOnline(charger, connectivity.online());
        } else if (event instanceof PlatformEvent.MetricReport report) {
            handleMetric(charger, report);
        } else if (event instanceof PlatformEvent.VendorAlert alert) {
            handleDeviceAlert(charger, alert);
        } else if (event instanceof PlatformEvent.CommandAck ack) {
            commandService.handleAck(deviceNo, ack);
        } else if (event instanceof PlatformEvent.OrderLifecycle lifecycle) {
            chargingService.handleOrderEvent(deviceNo, lifecycle);
        } else if (event instanceof PlatformEvent.PortStatus portStatus) {
            handlePortStatus(charger, portStatus);
        } else if (event instanceof PlatformEvent.TransactionStarted started) {
            ocppOrderBridge.onTransactionStarted(charger, started.connectorId(), started.idTag(),
                    started.meterStartKwh(), started.ts());
        } else if (event instanceof PlatformEvent.TransactionStopped stopped) {
            ocppOrderBridge.onTransactionStopped(charger, stopped.connectorId(), stopped.transactionId(),
                    stopped.meterStopKwh(), stopped.stopReason(), stopped.rawStopReason(), stopped.ts());
        }
    }

    /**
     * StatusNotification 只改端口状态，不带电量。
     *
     * <p>connectorId 与 {@code charger_port.port_no} 是同一个东西（OCPP 从 1 开始），
     * 0 是桩级状态，平台没有对应端口，直接忽略。
     */
    private void handlePortStatus(Charger charger, PlatformEvent.PortStatus event) {
        Integer connectorId = event.connectorId();
        if (connectorId == null || connectorId <= 0) {
            return;
        }
        ChargerPort port = portMapper.selectOne(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, charger.getId())
                .eq(ChargerPort::getPortNo, connectorId)
                .last("LIMIT 1"));
        if (port == null) {
            log.warn("StatusNotification for {} connector {} but no such port",
                    charger.getDeviceNo(), connectorId);
            return;
        }
        String status = event.status();
        // OCPP 拔枪只有状态帧、没有带 plugged 的测点：先按当前时间结清占位，再落端口状态。
        if ("IDLE".equals(status) && "OCCUPIED".equals(port.getStatus())
                && StringUtils.hasText(port.getOccupancyOrderNo())) {
            occupancyService.releaseForStart(charger.getId(), connectorId);
        }
        LambdaUpdateWrapper<ChargerPort> update = new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getId, port.getId())
                .set(ChargerPort::getStatus, status)
                .set(ChargerPort::getLastMetricAt, LocalDateTime.now());
        // 插枪信号只从状态里推：Preparing/Suspended/Finishing 都是「枪还在车上」，只有 Available 表示可拔。
        if ("CHARGING".equals(status) || "OCCUPIED".equals(status)) {
            update.set(ChargerPort::getPlugged, "1");
        } else if ("IDLE".equals(status)) {
            update.set(ChargerPort::getPlugged, "0");
        }
        portMapper.update(null, update);
        if ("FAULT".equals(status) && StringUtils.hasText(event.errorCode())
                && !"NoError".equalsIgnoreCase(event.errorCode())) {
            alertService.raise(charger, connectorId, AlertType.PORT_FAULT, null,
                    String.format("端口 %d 故障：%s", connectorId, event.errorCode()), null, null);
        }
    }

    private void handleOnline(Charger charger, boolean online) {
        String target = online ? "ONLINE" : "OFFLINE";
        LocalDateTime now = LocalDateTime.now();
        // 状态翻转用条件更新抢：共享订阅会把同一设备的相邻报文派给不同实例，
        // 读到的旧状态可能已经被对端改掉，直接判断会漏发或重发上下线事件。
        int flipped = chargerMapper.update(null, new LambdaUpdateWrapper<Charger>()
                .eq(Charger::getId, charger.getId())
                .ne(Charger::getOnlineStatus, target)
                .set(Charger::getOnlineStatus, target)
                .set(Charger::getLastOnlineAt, now));
        if (flipped == 0) {
            Charger refresh = new Charger();
            refresh.setId(charger.getId());
            // 心跳不改状态，但必须每帧续期 last_online_at，否则离线扫描会把在线设备误判成掉线
            refresh.setLastOnlineAt(now);
            chargerMapper.updateById(refresh);
        }
        shadowService.setOnline(charger.getDeviceNo(), online);
        if (flipped > 0) {
            onConnectivityChange(charger, online);
        }
    }

    /**
     * 设备状态真正翻转（由条件更新保证集群内只发生一次）：
     * 上线自动恢复离线告警并安排首帧测点做状态核对，下线产生离线告警。
     */
    private void onConnectivityChange(Charger charger, boolean online) {
        if (online) {
            alertService.autoResolve(charger, null, AlertType.OFFLINE);
            markReconcilePending(charger.getDeviceNo());
        } else {
            alertService.raise(charger, null, AlertType.OFFLINE, null,
                    String.format("设备 %s 离线", charger.getDeviceNo()), null, null);
        }
        publishDeviceEvent(charger, online);
    }

    private void handleDeviceAlert(Charger charger, PlatformEvent.VendorAlert alert) {
        AlertType type = AlertType.parse(alert.hint().alertTypeName());
        if (type == null) {
            // 适配器映射出的平台类型必须在枚举里；映射不上说明适配器契约没守，按端口故障兜住。
            type = AlertType.PORT_FAULT;
        }
        alertService.raiseFromDevice(charger, alert.portNo(), type,
                AlertLevel.fromText(alert.hint().level()), alert.message(),
                alert.value() == null ? null : BigDecimal.valueOf(alert.value()));
        log.info("Device {} reported {} alert on port {}", charger.getDeviceNo(), type, alert.portNo());
    }

    private void handleMetric(Charger charger, PlatformEvent.MetricReport report) {
        List<PortSample> samples = report.ports();
        if (samples.isEmpty()) {
            return;
        }

        long ts = report.ts() <= 0 ? System.currentTimeMillis() : report.ts();
        LocalDateTime metricTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
        LocalDateTime now = LocalDateTime.now();

        // 测点同样能把设备拉回在线；翻转条件化，避免两个实例同时处理相邻报文时重复发事件
        int backOnline = chargerMapper.update(null, new LambdaUpdateWrapper<Charger>()
                .eq(Charger::getId, charger.getId())
                .ne(Charger::getOnlineStatus, "ONLINE")
                .set(Charger::getOnlineStatus, "ONLINE"));
        Charger update = new Charger();
        update.setId(charger.getId());
        update.setLastOnlineAt(now);
        update.setLastMetricAt(now);
        chargerMapper.updateById(update);
        shadowService.setOnline(charger.getDeviceNo(), true);
        if (backOnline > 0) {
            onConnectivityChange(charger, true);
        }

        Map<Integer, ChargerPort> portByNo = new HashMap<>();
        for (ChargerPort port : portMapper.selectByChargerId(charger.getId())) {
            portByNo.put(port.getPortNo(), port);
        }
        boolean reconcileReconnect = consumeReconcilePending(charger.getDeviceNo());

        List<MetricPoint> points = new ArrayList<>(samples.size());
        List<PortMetric> shadowPorts = new ArrayList<>(samples.size());
        for (PortSample sample : samples) {
            PortMetric metric = toMetric(sample);
            ChargerPort port = metric.getPortNo() == null ? null : portByNo.get(metric.getPortNo());
            if (port == null) {
                continue;
            }
            // 占用结算必须先于端口状态落库：拔枪帧会把状态归一为 IDLE，之后再判占用就晚了。
            if ("OCCUPIED".equals(port.getStatus()) && metric.getPlugged() != null) {
                occupancyService.onPortMetric(charger.getId(), port.getPortNo(), metric);
            }
            String status = normalizeStatus(metric);
            if ("IDLE".equals(status) && Boolean.TRUE.equals(metric.getPlugged())
                    && !StringUtils.hasText(port.getCurrentOrderNo())) {
                status = "OCCUPIED";
            }
            metric.setStatus(status);
            metric.setTs(ts);
            shadowPorts.add(metric);

            ChargerPort portUpdate = new ChargerPort();
            portUpdate.setId(port.getId());
            portUpdate.setStatus(status);
            portUpdate.setLastMetricAt(now);
            if (metric.getPlugged() != null) {
                portUpdate.setPlugged(metric.getPlugged() ? "1" : "0");
            }
            portMapper.updateById(portUpdate);

            points.add(new MetricPoint(
                    charger.getId(),
                    charger.getStationId(),
                    metric.getPortNo(),
                    metricTime,
                    value(metric.getVoltage()),
                    value(metric.getCurrentA()),
                    value(metric.getTemperature()),
                    value(metric.getPowerKw()),
                    value(metric.getEnergyTotal()),
                    metric.getSignalStrength() == null ? 0 : metric.getSignalStrength(),
                    // 入队时盖章：写库发生在 flush 线程，那时读到的是默认租户
                    TenantContext.get()
            ));
            alertEvaluator.evaluate(charger, port, metric);
            if (reconcileReconnect) {
                reconcileOrderState(charger, port, status);
            }
            chargingService.onMetric(charger, metric.getPortNo(), metric, ts);
        }
        shadowService.save(charger.getDeviceNo(), shadowPorts);
        points.forEach(metricIngestService::enqueue);
    }

    /**
     * 设备重连后首帧测点：平台仍认为端口在充电、设备却上报空闲，说明断连期间订单已中断。
     * 只产生状态不一致告警，订单由设备停止事件或超时扫描收敛。
     */
    private void reconcileOrderState(Charger charger, ChargerPort port, String status) {
        if (!"IDLE".equals(status) || !StringUtils.hasText(port.getCurrentOrderNo())) {
            return;
        }
        alertService.raise(charger, port.getPortNo(), AlertType.STATE_MISMATCH, null,
                String.format("设备重连后端口 %d 上报空闲，平台订单 %s 仍在充电", port.getPortNo(), port.getCurrentOrderNo()),
                null, null);
    }

    private void publishDeviceEvent(Charger charger, boolean online) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chargerId", charger.getId());
        data.put("stationId", charger.getStationId());
        data.put("deviceNo", charger.getDeviceNo());
        data.put("deviceName", charger.getName());
        data.put("onlineStatus", online ? "ONLINE" : "OFFLINE");
        eventStreamService.publish("device", charger.getStationId(), data);
    }

    /**
     * 端口采样的协议无关表示转回计费/占位在用的 {@link PortMetric}。
     * 车类字段（SOC/BMS/枪温）在 Step 7 把下游换成 {@link PortSample} 之前还传不下去。
     */
    private PortMetric toMetric(PortSample sample) {
        PortMetric metric = new PortMetric();
        metric.setPortNo(sample.portNo());
        metric.setStatus(sample.status());
        metric.setVoltage(sample.voltage());
        metric.setCurrentA(sample.currentA());
        metric.setTemperature(sample.temperature());
        metric.setPowerKw(sample.powerKw());
        metric.setEnergyTotal(sample.energyTotal());
        metric.setSignalStrength(sample.signalStrength());
        metric.setPlugged(sample.plugged());
        return metric;
    }

    private String normalizeStatus(PortMetric metric) {
        String status = metric.getStatus();
        if ("CHARGING".equals(status) || "FAULT".equals(status) || "IDLE".equals(status)) {
            return status;
        }
        double current = value(metric.getCurrentA());
        return current > 0.1 ? "CHARGING" : "IDLE";
    }

    private double value(Double number) {
        return number == null ? 0D : number;
    }

    private void markReconcilePending(String deviceNo) {
        if (!StringUtils.hasText(deviceNo)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(reconcileKey(deviceNo), "1", RECONCILE_TTL);
        } catch (Exception e) {
            log.warn("Failed to mark reconnect reconcile for {}: {}", deviceNo, e.getMessage());
        }
    }

    /** 标记只消费一次：下一帧测点无论落在哪个实例，读到的都是同一个键。 */
    private boolean consumeReconcilePending(String deviceNo) {
        if (!StringUtils.hasText(deviceNo)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(reconcileKey(deviceNo)));
        } catch (Exception e) {
            log.warn("Failed to read reconnect reconcile for {}: {}", deviceNo, e.getMessage());
            return false;
        }
    }

    private String reconcileKey(String deviceNo) {
        return RECONCILE_PREFIX + TenantContext.get() + ":" + deviceNo;
    }

    @Scheduled(fixedDelay = OFFLINE_SWEEP_SECONDS * 1000, initialDelay = 30_000)
    public void markStaleDevicesOffline() {
        tenantTasks.forEach("device-offline-sweep", Duration.ofSeconds(OFFLINE_SWEEP_SECONDS),
                Duration.ofSeconds(OFFLINE_SWEEP_SECONDS - 5), this::markStaleDevicesOfflineForTenant);
    }

    private void markStaleDevicesOfflineForTenant() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_AFTER_SECONDS);
        List<Charger> staleDevices = chargerMapper.selectStaleOnline(threshold);
        for (Charger charger : staleDevices) {
            Charger update = new Charger();
            update.setId(charger.getId());
            update.setOnlineStatus("OFFLINE");
            chargerMapper.updateById(update);
            shadowService.setOnline(charger.getDeviceNo(), false);
            if (!StringUtils.hasText(charger.getDeviceNo())) {
                continue;
            }
            log.info("Device {} marked offline (no heartbeat for {}s)", charger.getDeviceNo(), OFFLINE_AFTER_SECONDS);
            alertService.raise(charger, null, AlertType.OFFLINE, null,
                    String.format("设备 %s 超过 %d 秒无心跳，判定离线", charger.getDeviceNo(), OFFLINE_AFTER_SECONDS),
                    null, null);
            publishDeviceEvent(charger, false);
        }
    }
}
