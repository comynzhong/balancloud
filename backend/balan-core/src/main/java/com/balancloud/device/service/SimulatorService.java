package com.balancloud.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.SimConfig;
import com.balancloud.device.dto.FaultInjectRequest;
import com.balancloud.device.dto.SimBatchRequest;
import com.balancloud.device.dto.SimConfigRequest;
import com.balancloud.device.dto.SimUnplugRequest;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.SimConfigMapper;
import com.balancloud.device.vo.ChargerVo;
import com.balancloud.device.vo.SimulatorStatusVo;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.framework.tenant.TenantTaskExecutor;
import com.balancloud.mqtt.MqttGateway;
import com.balancloud.mqtt.event.DownlinkCommandEvent;
import com.balancloud.mqtt.model.MetricPayload;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long ONLINE_HEARTBEAT_MS = 30_000;
    private static final long RUNTIME_REFRESH_MS = 10_000;
    private static final Duration LEADER_TTL = Duration.ofSeconds(30);
    private static final long TICK_PERIOD_MS = 1000;
    private static final double METER_STALE_KWH = 1.0D;

    private final SimConfigMapper simConfigMapper;
    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final ChargeOrderMapper orderMapper;
    private final ChargerService chargerService;
    private final StationScopeService scopeService;
    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final TenantTaskExecutor tenantTasks;
    private final DeviceTenantResolver tenantResolver;
    private final Random random = new Random();

    private final Map<Long, DeviceRuntime> runtimes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PendingOrderEvent> pendingOrderEvents = new ConcurrentLinkedQueue<>();

    /** 运行状态与节拍都按租户持有：一个租户启停仿真不能影响别的租户。 */
    private final Map<Long, SimState> states = new ConcurrentHashMap<>();

    public SimulatorService(SimConfigMapper simConfigMapper,
                            ChargerMapper chargerMapper,
                            ChargerPortMapper portMapper,
                            ChargeOrderMapper orderMapper,
                            ChargerService chargerService,
                            StationScopeService scopeService,
                            MqttGateway mqttGateway,
                            ObjectMapper objectMapper,
                            TenantTaskExecutor tenantTasks,
                            DeviceTenantResolver tenantResolver) {
        this.simConfigMapper = simConfigMapper;
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.orderMapper = orderMapper;
        this.chargerService = chargerService;
        this.scopeService = scopeService;
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
        this.tenantTasks = tenantTasks;
        this.tenantResolver = tenantResolver;
    }

    private static final class SimState {
        private volatile boolean running;
        private volatile LocalDateTime startedAt;
        private volatile long lastMetricAttemptAt;
        private volatile long lastOnlineAt;
        private volatile long lastRefreshAt;
        private volatile long lastPublishAt;
    }

    private SimState stateFor(Long tenantId) {
        return states.computeIfAbsent(tenantId, id -> new SimState());
    }

    private SimState currentState() {
        return stateFor(TenantContext.get());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        tenantTasks.forEachOnce("simulator-init", this::autoStartForTenant);
    }

    private void autoStartForTenant() {
        SimState state = currentState();
        SimConfig config = loadConfig();
        if (config != null && config.getEnabled() != null && config.getEnabled() == 1) {
            state.running = true;
            state.startedAt = LocalDateTime.now();
            refreshRuntimes();
            log.info("Simulator auto-started for tenant {} with {} devices", TenantContext.get(), countRuntimes());
        }
    }

    @Scheduled(fixedDelay = TICK_PERIOD_MS, initialDelay = 3500)
    public void tick() {
        // 待发订单事件由各节点自己补发，不等 leader：它记录的是本实例处理过的停机
        flushPendingOrderEvents();
        // 端口状态在各自实例内存里，双实例同时上报会出现两份互相跳变的电表读数；
        // 心跳与测点只由每个租户的 leader 实例发。
        // TTL 必须明显大于一轮租户 tick（DEBUG + 多租户下实测 5–10 秒），否则租约会在
        // 相邻两轮之间过期，备用实例抢到 leader 后两边同时发测点。
        tenantTasks.forEachLeader("simulator", Duration.ofMillis(TICK_PERIOD_MS), LEADER_TTL, this::tickForTenant);
    }

    private void tickForTenant() {
        SimState state = currentState();
        SimConfig config = loadConfig();
        if (config == null || config.getEnabled() == null || config.getEnabled() == 0) {
            if (state.running) {
                log.info("Simulator stopped by configuration for tenant {}", TenantContext.get());
            }
            state.running = false;
            return;
        }
        // 启停以 sim_config 为准，不看本实例的内存标记：备用实例接管 leader 后
        // 也要立刻开始发心跳与测点，否则它永远停在关机态、把订单电表留在自己的随机基线上。
        if (!state.running) {
            state.startedAt = LocalDateTime.now();
            log.info("Simulator started from configuration for tenant {}", TenantContext.get());
        }
        state.running = true;
        long now = System.currentTimeMillis();
        if (now - state.lastRefreshAt >= RUNTIME_REFRESH_MS) {
            refreshRuntimes();
            state.lastRefreshAt = now;
        }
        int intervalSeconds = Math.max(config.getIntervalSeconds(), 1);
        if (now - state.lastOnlineAt >= ONLINE_HEARTBEAT_MS) {
            publishOnline(true);
            state.lastOnlineAt = now;
        }
        if (now - state.lastMetricAttemptAt >= intervalSeconds * 1000L) {
            state.lastMetricAttemptAt = now;
            publishMetrics(config, now);
        }
    }

    public SimulatorStatusVo status() {
        SimConfig config = loadConfig();
        SimState state = currentState();
        List<Charger> devices = chargerMapper.selectSimulated();
        SimulatorStatusVo vo = new SimulatorStatusVo();
        vo.setEnabled(config != null && config.getEnabled() != null && config.getEnabled() == 1);
        vo.setRunning(state.running);
        vo.setIntervalSeconds(config == null || config.getIntervalSeconds() == null ? 5 : config.getIntervalSeconds());
        vo.setFaultProbability(config == null ? null : config.getFaultProbability());
        vo.setDeviceCount(devices.size());
        vo.setPortCount(devices.stream().mapToLong(charger -> charger.getPortCount() == null ? 0 : charger.getPortCount()).sum());
        vo.setMqttConnected(mqttGateway.isConnected());
        vo.setBrokerUrl(mqttGateway.getBrokerUrl());
        vo.setStartedAt(state.startedAt);
        long published = state.lastPublishAt;
        vo.setLastPublishAt(published == 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(published), ZONE));
        return vo;
    }

    public SimulatorStatusVo start() {
        SimConfig config = requireConfig();
        config.setEnabled(1);
        config.setUpdatedBy(SecurityUtils.getUsername());
        simConfigMapper.updateById(config);
        SimState state = currentState();
        state.running = true;
        state.startedAt = LocalDateTime.now();
        state.lastPublishAt = 0;
        state.lastMetricAttemptAt = 0;
        state.lastOnlineAt = 0;
        refreshRuntimes();
        state.lastRefreshAt = System.currentTimeMillis();
        publishOnline(true);
        state.lastOnlineAt = System.currentTimeMillis();
        log.info("Simulator started for tenant {} with {} devices", TenantContext.get(), countRuntimes());
        return status();
    }

    public SimulatorStatusVo stop() {
        SimConfig config = requireConfig();
        config.setEnabled(0);
        config.setUpdatedBy(SecurityUtils.getUsername());
        simConfigMapper.updateById(config);
        SimState state = currentState();
        state.running = false;
        publishOnline(false);
        // 端口运行时状态只在内存里，不清会让下一轮启动带着上一次的注入故障上报测点，
        // 凭空产生过温/端口故障告警。充电中的订单由 start() 按数据库重新恢复。
        dropTenantRuntimes();
        log.info("Simulator stopped for tenant {}", TenantContext.get());
        return status();
    }

    public SimulatorStatusVo updateConfig(SimConfigRequest request) {
        SimConfig config = requireConfig();
        config.setIntervalSeconds(request.getIntervalSeconds());
        config.setFaultProbability(request.getFaultProbability());
        config.setUpdatedBy(SecurityUtils.getUsername());
        simConfigMapper.updateById(config);
        return status();
    }

    public List<ChargerVo> createBatch(SimBatchRequest request) {
        List<ChargerVo> created = chargerService.createSimulatedBatch(request);
        refreshRuntimes();
        SimState state = currentState();
        if (state.running) {
            publishOnline(true);
            state.lastOnlineAt = System.currentTimeMillis();
            state.lastMetricAttemptAt = 0;
        }
        return created;
    }

    public SimulatorStatusVo injectFault(FaultInjectRequest request) {
        Charger charger = chargerMapper.selectByDeviceNo(request.getDeviceNo());
        if (charger == null) {
            throw new ServiceException(404, "设备不存在");
        }
        scopeService.assertAccess(charger.getStationId());
        if (charger.getIsSimulated() == null || charger.getIsSimulated() != 1) {
            throw new ServiceException(400, "仅仿真设备支持故障注入");
        }
        SimState state = currentState();
        if (!state.running) {
            throw new ServiceException(400, "仿真器未运行，请先启动仿真");
        }
        DeviceRuntime runtime = runtimeFor(charger.getId());
        if (runtime == null) {
            throw new ServiceException(400, "仿真设备未加载，请稍后重试");
        }
        PortState port = runtime.ports.get(request.getPortNo());
        if (port == null) {
            throw new ServiceException(400, "端口不存在");
        }
        long now = System.currentTimeMillis();
        String activeOrder;
        String faultType;
        double finalMeter;
        synchronized (port) {
            activeOrder = port.activeOrderNo;
            port.activeOrderNo = null;
            port.status = "FAULT";
            port.plugged = false;
            port.faultUntil = now + request.getDurationSeconds() * 1000L;
            port.faultType = StringUtils.hasText(request.getFaultType()) ? request.getFaultType() : "OVER_TEMP";
            faultType = port.faultType;
            finalMeter = port.energyTotal;
        }
        publishAlert(charger, port.portNo, faultType, faultTemperature(), now);
        publishDeviceMetrics(runtime, now, 1, 0, new ArrayList<>());
        if (activeOrder != null) {
            publishOrderEvent(charger.getDeviceNo(), port.portNo, activeOrder, "FAULT", finalMeter, now);
        }
        state.lastMetricAttemptAt = 0;
        log.info("Injected {} on {}/port{} for {}s", port.faultType, charger.getDeviceNo(),
                port.portNo, request.getDurationSeconds());
        return status();
    }

    /**
     * 模拟用户拔枪：只改变插枪状态并上报测点，平台据此结束占用计费。
     */
    public SimulatorStatusVo unplug(SimUnplugRequest request) {
        Charger charger = chargerMapper.selectByDeviceNo(request.getDeviceNo());
        if (charger == null) {
            throw new ServiceException(404, "设备不存在");
        }
        scopeService.assertAccess(charger.getStationId());
        if (charger.getIsSimulated() == null || charger.getIsSimulated() != 1) {
            throw new ServiceException(400, "仅仿真设备支持模拟拔枪");
        }
        SimState state = currentState();
        if (!state.running) {
            throw new ServiceException(400, "仿真器未运行，请先启动仿真");
        }
        DeviceRuntime runtime = runtimeFor(charger.getId());
        if (runtime == null) {
            throw new ServiceException(400, "仿真设备未加载，请稍后重试");
        }
        PortState port = runtime.ports.get(request.getPortNo());
        if (port == null) {
            throw new ServiceException(400, "端口不存在");
        }
        synchronized (port) {
            if (port.activeOrderNo != null) {
                throw new ServiceException(400, "端口正在充电，请先停止充电");
            }
            port.plugged = false;
        }
        long now = System.currentTimeMillis();
        publishDeviceMetrics(runtime, now, 1, 0, new ArrayList<>());
        state.lastMetricAttemptAt = 0;
        log.info("Simulated unplug on {}/port{}", charger.getDeviceNo(), port.portNo);
        return status();
    }

    @EventListener
    public void onDownlinkCommand(DownlinkCommandEvent event) {
        Long tenantId = tenantResolver.resolve(event.getDeviceNo());
        if (tenantId == null) {
            return;
        }
        // 下行指令普通订阅，每个实例都收得到；仿真设备只由所属租户的 Leader 实例扮演，
        // 否则同一指令会在两个实例上各跑一遍，端口状态与电表读数会分叉。
        if (!tenantTasks.holdsLeader("simulator", tenantId, LEADER_TTL)) {
            return;
        }
        // MQTT 回调线程没有会话上下文，不显式切换租户的话运行态查不到设备影子。
        TenantContext.use(tenantId);
        try {
            if (!configEnabled()) {
                return;
            }
            DeviceRuntime runtime = findRuntime(event.getDeviceNo(), tenantId);
            if (runtime != null) {
                handleCommand(runtime, event);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void handleCommand(DeviceRuntime runtime, DownlinkCommandEvent event) {
        JsonNode root;
        try {
            root = objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            log.warn("Invalid command payload for {}: {}", event.getDeviceNo(), event.getPayload());
            return;
        }
        String cmdId = root.path("cmdId").asText(null);
        String type = root.path("type").asText("");
        Integer portNo = root.path("portNo").isInt() ? root.path("portNo").asInt() : null;
        if (!StringUtils.hasText(cmdId) || portNo == null) {
            log.warn("Command without cmdId/portNo for {}", event.getDeviceNo());
            return;
        }
        PortState port = runtime.ports.get(portNo);
        if (port == null) {
            ack(runtime.charger, cmdId, portNo, type, false, "端口不存在", System.currentTimeMillis());
            return;
        }
        JsonNode params = root.path("params");
        // Leader 迁移后接手方内存里没有在充会话，但 charger_port 台账是准的；
        // 不先按台账补齐，STOP 会被误判成「端口没有进行中的充电」。
        if ("STOP".equals(type) || "UNLOCK".equals(type)) {
            adoptOrderFromLedger(runtime, port);
        }
        switch (type) {
            case "START" -> handleStart(runtime, port, cmdId, params);
            case "STOP" -> handleStop(runtime, port, cmdId, params);
            case "UNLOCK" -> handleUnlock(runtime, port, cmdId);
            case "SET_THRESHOLD" -> handleThreshold(runtime, port, cmdId, params);
            default -> ack(runtime.charger, cmdId, portNo, type, false,
                    "不支持的指令类型", System.currentTimeMillis());
        }
    }

    private void adoptOrderFromLedger(DeviceRuntime runtime, PortState port) {
        if (port.activeOrderNo != null) {
            return;
        }
        ChargerPort row = portMapper.selectOne(new LambdaQueryWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, runtime.charger.getId())
                .eq(ChargerPort::getPortNo, port.portNo));
        ChargeOrder order = row == null ? null : findOrder(row.getCurrentOrderNo());
        // STOP 指令是订单先落 STOPPING 再下发的，接手时订单状态通常已经是 STOPPING；
        // 只认 CHARGING 会让换届后的 STOP 继续回「端口没有进行中的充电」。
        if (order == null || isTerminal(order)
                || !(OrderStatus.CHARGING.name().equals(order.getStatus())
                || OrderStatus.STOPPING.name().equals(order.getStatus()))) {
            return;
        }
        synchronized (port) {
            port.status = "CHARGING";
            port.activeOrderNo = order.getOrderNo();
            port.startedAtMs = order.getStartTime() == null
                    ? System.currentTimeMillis()
                    : order.getStartTime().atZone(ZONE).toInstant().toEpochMilli();
            if (order.getLastMeter() != null) {
                port.energyTotal = order.getLastMeter().doubleValue();
            } else if (order.getStartMeter() != null) {
                port.energyTotal = order.getStartMeter().doubleValue();
            }
        }
        log.info("Adopted charging order {} for {}/port{} after leader handover",
                order.getOrderNo(), runtime.charger.getDeviceNo(), port.portNo);
    }

    private void handleStart(DeviceRuntime runtime, PortState port, String cmdId, JsonNode params) {
        long now = System.currentTimeMillis();
        String orderNo = params.path("orderNo").asText(null);
        String message = null;
        if (!StringUtils.hasText(orderNo)) {
            message = "缺少订单号";
        }
        synchronized (port) {
            if (message == null && "FAULT".equals(port.status) && now >= port.faultUntil) {
                port.status = "IDLE";
                port.faultType = null;
            }
            if (message == null && (!"IDLE".equals(port.status) || port.activeOrderNo != null)) {
                message = "FAULT".equals(port.status) ? "端口故障，无法启动" : "端口正忙，无法启动";
            }
            if (message == null) {
                port.status = "CHARGING";
                port.activeOrderNo = orderNo;
                port.startedAtMs = now;
                port.faultType = null;
                port.plugged = true;
            }
        }
        ack(runtime.charger, cmdId, port.portNo, "START", message == null,
                message == null ? "启动成功" : message, now);
        if (message == null) {
            publishDeviceMetrics(runtime, now, 1, 0, new ArrayList<>());
        }
    }

    private void handleStop(DeviceRuntime runtime, PortState port, String cmdId, JsonNode params) {
        long now = System.currentTimeMillis();
        String requestedOrderNo = params.path("orderNo").asText(null);
        String reason = params.path("reason").asText("USER_STOP");
        String stoppedOrderNo = null;
        String message = null;
        double finalMeter = port.energyTotal;
        synchronized (port) {
            if (port.activeOrderNo == null) {
                message = "端口没有进行中的充电";
            } else if (StringUtils.hasText(requestedOrderNo)
                    && !requestedOrderNo.equals(port.activeOrderNo)) {
                message = "订单号不匹配";
            } else {
                stoppedOrderNo = port.activeOrderNo;
                finalMeter = port.energyTotal;
                port.activeOrderNo = null;
                port.status = "IDLE";
                port.startedAtMs = 0;
            }
        }
        if (message != null) {
            ack(runtime.charger, cmdId, port.portNo, "STOP", false, message, now);
            return;
        }
        publishDeviceMetrics(runtime, now, 1, 0, new ArrayList<>());
        ack(runtime.charger, cmdId, port.portNo, "STOP", true, "停止成功", now);
        publishOrderEvent(runtime.charger.getDeviceNo(), port.portNo, stoppedOrderNo,
                reason, finalMeter, now);
    }

    private void handleUnlock(DeviceRuntime runtime, PortState port, String cmdId) {
        long now = System.currentTimeMillis();
        boolean success;
        synchronized (port) {
            success = port.activeOrderNo == null;
            if (success) {
                port.status = "IDLE";
                port.faultUntil = 0;
                port.faultType = null;
            }
        }
        ack(runtime.charger, cmdId, port.portNo, "UNLOCK", success,
                success ? "解锁成功" : "端口正在充电，无法解锁", now);
        if (success) {
            publishDeviceMetrics(runtime, now, 1, 0, new ArrayList<>());
        }
    }

    private void handleThreshold(DeviceRuntime runtime, PortState port, String cmdId, JsonNode params) {
        long now = System.currentTimeMillis();
        if (params.hasNonNull("overTempC")) {
            port.overTempC = params.path("overTempC").asDouble(port.overTempC);
        }
        if (params.hasNonNull("overCurrentA")) {
            port.overCurrentA = params.path("overCurrentA").asDouble(port.overCurrentA);
        }
        ack(runtime.charger, cmdId, port.portNo, "SET_THRESHOLD", true,
                "阈值已更新", now);
    }

    private void ack(Charger charger, String cmdId, int portNo, String type,
                     boolean success, String message, long now) {
        mqttGateway.publishUplink(charger.getDeviceNo(), "cmd_ack",
                ackEnvelope(charger.getDeviceNo(), cmdId, portNo, type, success, message, now));
    }

    /**
     * 仿真设备回给平台的 cmd_ack 正文，也就是内置 BALAN_MQTT 方言的回执形状。
     * 单独抽出来是给协议一致性测试当对端用的（§46 Step 3）：adapter 必须能解掉真实发出去的这一帧，
     * 而不是测试里另写一份「以为它是这样」的报文。
     */
    public static Map<String, Object> ackEnvelope(String deviceNo, String cmdId, int portNo, String type,
                                                  boolean success, String message, long now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cmdId", cmdId);
        payload.put("deviceId", deviceNo);
        payload.put("portNo", portNo);
        payload.put("type", type);
        payload.put("result", success ? "SUCCESS" : "FAILED");
        payload.put("message", message);
        payload.put("ts", now);
        return payload;
    }

    private void refreshRuntimes() {
        long tenantId = TenantContext.get();
        List<Charger> devices = chargerMapper.selectSimulated();
        Map<Long, Charger> current = new HashMap<>();
        for (Charger charger : devices) {
            current.put(charger.getId(), charger);
            DeviceRuntime runtime = runtimes.computeIfAbsent(charger.getId(), id -> new DeviceRuntime());
            runtime.tenantId = tenantId;
            runtime.charger = charger;
            List<ChargerPort> ports = portMapper.selectByChargerId(charger.getId());
            for (ChargerPort port : ports) {
                PortState state = runtime.ports.computeIfAbsent(port.getPortNo(), key -> new PortState());
                state.portNo = port.getPortNo();
                state.ratedPowerKw = port.getRatedPowerKw() == null ? 2.2D : port.getRatedPowerKw().doubleValue();
                if (!state.initialized) {
                    initializePort(charger, state, port);
                } else {
                    reconcilePort(state, port);
                    reanchorPort(charger, state, port);
                }
            }
            runtime.ports.keySet().removeIf(portNo -> ports.stream().noneMatch(port -> port.getPortNo().equals(portNo)));
        }
        // 只淘汰本租户的删除设备：全局清理会把别的租户正在跑的端口影子一起丢掉
        runtimes.entrySet().removeIf(entry -> entry.getValue().tenantId == tenantId
                && !current.containsKey(entry.getKey()));
    }

    private void initializePort(Charger charger, PortState state, ChargerPort port) {
        state.initialized = true;
        state.status = "IDLE";
        state.activeOrderNo = null;
        state.plugged = "1".equals(port.getPlugged());
        state.energyTotal = 20 + random.nextDouble() * 780;
        ChargeOrder order = findOrder(port.getCurrentOrderNo());
        if (order == null || isTerminal(order)) {
            return;
        }
        if (order.getLastMeter() != null) {
            state.energyTotal = order.getLastMeter().doubleValue();
        } else if (order.getStartMeter() != null) {
            state.energyTotal = order.getStartMeter().doubleValue();
        }
        if (!OrderStatus.CHARGING.name().equals(order.getStatus())
                && !OrderStatus.STOPPING.name().equals(order.getStatus())) {
            return;
        }
        state.status = "CHARGING";
        state.activeOrderNo = order.getOrderNo();
        state.startedAtMs = order.getStartTime() == null
                ? System.currentTimeMillis()
                : order.getStartTime().atZone(ZONE).toInstant().toEpochMilli();
        if (OrderStatus.STOPPING.name().equals(order.getStatus())) {
            state.status = "IDLE";
            state.activeOrderNo = null;
            pendingOrderEvents.add(new PendingOrderEvent(charger.getDeviceNo(), state.portNo,
                    order.getOrderNo(), stopReason(order), state.energyTotal, System.currentTimeMillis()));
            log.info("Recovered stopping order {} for {}/port{}", order.getOrderNo(),
                    charger.getDeviceNo(), state.portNo);
        } else {
            log.info("Recovered charging order {} for {}/port{} at {} kWh", order.getOrderNo(),
                    charger.getDeviceNo(), state.portNo, String.format("%.3f", state.energyTotal));
        }
    }

    private void reconcilePort(PortState state, ChargerPort port) {
        if (state.activeOrderNo == null) {
            return;
        }
        ChargeOrder order = findOrder(state.activeOrderNo);
        boolean released = !state.activeOrderNo.equals(port.getCurrentOrderNo());
        boolean closed = order == null || isTerminal(order);
        if (!released && !closed) {
            return;
        }
        String releasedOrder = state.activeOrderNo;
        synchronized (state) {
            state.activeOrderNo = null;
            if ("CHARGING".equals(state.status)) {
                state.status = "IDLE";
            }
        }
        log.info("Simulator port {} released order {}", port.getPortNo(), releasedOrder);
    }

    /**
     * 台账是电表读数的唯一真相：实例不是 leader 时内存影子停在启动那轮的随机基线，
     * 接手后直接上报会把在充订单炸成几百度的异常单（双实例实测 24.88 → 442 kWh）。
     * 正常上报只比台账超前不到一个间隔，所以只在「影子丢单」或「偏差超一个额定功率量级」时重锚。
     */
    private void reanchorPort(Charger charger, PortState state, ChargerPort port) {
        ChargeOrder order = findOrder(port.getCurrentOrderNo());
        if (order == null || isTerminal(order)) {
            return;
        }
        boolean missing = state.activeOrderNo == null && OrderStatus.CHARGING.name().equals(order.getStatus());
        boolean drifting = order.getLastMeter() != null
                && meterIsStale(state.energyTotal, order.getLastMeter().doubleValue(), state.ratedPowerKw);
        if (!missing && !drifting) {
            return;
        }
        log.warn("Re-anchoring {}/port{} to ledger order {} (meter {} -> {}, status {})",
                charger.getDeviceNo(), state.portNo, order.getOrderNo(),
                String.format("%.4f", state.energyTotal), order.getLastMeter(), state.status);
        state.initialized = false;
        initializePort(charger, state, port);
    }

    static boolean meterIsStale(double memoryKwh, double ledgerKwh, double ratedPowerKw) {
        return Math.abs(ledgerKwh - memoryKwh) > METER_STALE_KWH + Math.max(0D, ratedPowerKw);
    }

    private ChargeOrder findOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
    }

    private boolean isTerminal(ChargeOrder order) {
        try {
            return OrderStatus.parse(order.getStatus()).isTerminal();
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private String stopReason(ChargeOrder order) {
        return StringUtils.hasText(order.getStopReason()) ? order.getStopReason() : "DEVICE_STOP";
    }

    private DeviceRuntime findRuntime(String deviceNo, long tenantId) {
        if (!StringUtils.hasText(deviceNo)) {
            return null;
        }
        return runtimes.values().stream()
                .filter(runtime -> runtime.tenantId == tenantId)
                .filter(runtime -> deviceNo.equals(runtime.charger.getDeviceNo()))
                .findFirst()
                .orElse(null);
    }

    private DeviceRuntime runtimeFor(Long chargerId) {
        DeviceRuntime runtime = runtimes.get(chargerId);
        if (runtime != null && runtime.tenantId == TenantContext.get()) {
            return runtime;
        }
        refreshRuntimes();
        runtime = runtimes.get(chargerId);
        return runtime != null && runtime.tenantId == TenantContext.get() ? runtime : null;
    }

    private int countRuntimes() {
        long tenantId = TenantContext.get();
        return (int) runtimes.values().stream().filter(runtime -> runtime.tenantId == tenantId).count();
    }

    private void dropTenantRuntimes() {
        long tenantId = TenantContext.get();
        runtimes.entrySet().removeIf(entry -> entry.getValue().tenantId == tenantId);
    }

    private void flushPendingOrderEvents() {
        if (!mqttGateway.isConnected()) {
            return;
        }
        PendingOrderEvent event;
        while ((event = pendingOrderEvents.poll()) != null) {
            publishOrderEvent(event.deviceNo(), event.portNo(), event.orderNo(),
                    event.reason(), event.energyTotal(), event.ts());
        }
    }

    private void publishOnline(boolean online) {
        long now = System.currentTimeMillis();
        for (DeviceRuntime runtime : sortedRuntimes()) {
            Charger charger = runtime.charger;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceId", charger.getDeviceNo());
            payload.put("online", online);
            payload.put("ts", now);
            payload.put("firmwareVersion", charger.getFirmwareVersion());
            mqttGateway.publishUplink(charger.getDeviceNo(), "online", payload);
        }
    }

    private void publishMetrics(SimConfig config, long now) {
        int intervalSeconds = Math.max(config.getIntervalSeconds(), 1);
        double faultProbability = config.getFaultProbability() == null ? 0 : config.getFaultProbability().doubleValue();
        boolean published = false;
        for (DeviceRuntime runtime : sortedRuntimes()) {
            List<PortOrderStop> stops = new ArrayList<>();
            if (publishDeviceMetrics(runtime, now, intervalSeconds, faultProbability, stops)) {
                published = true;
            }
            for (PortOrderStop stop : stops) {
                publishOrderEvent(runtime.charger.getDeviceNo(), stop.portNo(),
                        stop.orderNo(), "FAULT", stop.energyTotal(), stop.ts());
            }
        }
        if (published) {
            currentState().lastPublishAt = now;
        }
    }

    private boolean publishDeviceMetrics(DeviceRuntime runtime, long now, int intervalSeconds,
                                         double faultProbability, List<PortOrderStop> stops) {
        Charger charger = runtime.charger;
        MetricPayload payload = new MetricPayload();
        payload.setDeviceId(charger.getDeviceNo());
        payload.setTs(now);
        List<PortMetric> metrics = new ArrayList<>(runtime.ports.size());
        for (PortState port : runtime.ports.values()) {
            metrics.add(simulatePort(charger, port, intervalSeconds, faultProbability, now, stops));
        }
        metrics.sort(Comparator.comparingInt(PortMetric::getPortNo));
        payload.setPorts(metrics);
        return mqttGateway.publishUplink(charger.getDeviceNo(), "metric", payload);
    }

    private PortMetric simulatePort(Charger charger, PortState port, int intervalSeconds,
                                    double faultProbability, long now, List<PortOrderStop> stops) {
        synchronized (port) {
            if ("FAULT".equals(port.status) && now >= port.faultUntil) {
                port.status = "IDLE";
                port.faultType = null;
            }
            if (!"FAULT".equals(port.status) && random.nextDouble() < faultProbability) {
                port.status = "FAULT";
                port.faultUntil = now + (20_000 + (long) (random.nextDouble() * 20_000));
                port.faultType = "OVER_TEMP";
                port.plugged = false;
                publishAlert(charger, port.portNo, port.faultType, faultTemperature(), now);
                if (port.activeOrderNo != null) {
                    stops.add(new PortOrderStop(port.activeOrderNo, port.portNo, port.energyTotal, now));
                    port.activeOrderNo = null;
                }
            }

            PortMetric metric = new PortMetric();
            metric.setPortNo(port.portNo);
            metric.setStatus(port.status);
            metric.setTs(now);
            metric.setPlugged(port.plugged);
            metric.setSignalStrength(-45 - random.nextInt(31));
            if ("CHARGING".equals(port.status)) {
                double voltage = 218 + random.nextDouble() * 8;
                double current = 5.5 + random.nextDouble() * Math.min(4.4, port.ratedPowerKw * 1000 / 220 * 0.45);
                double power = voltage * current / 1000D;
                port.energyTotal += power * intervalSeconds / 3600D;
                metric.setVoltage(round(voltage, 2));
                metric.setCurrentA(round(current, 2));
                metric.setPowerKw(round(power, 3));
                metric.setTemperature(round(38 + current / 10D * 15 + random.nextDouble() * 4, 2));
                metric.setEnergyTotal(round(port.energyTotal, 4));
            } else if ("FAULT".equals(port.status)) {
                metric.setVoltage(0D);
                metric.setCurrentA(0D);
                metric.setPowerKw(0D);
                metric.setTemperature(faultTemperature());
                metric.setEnergyTotal(round(port.energyTotal, 4));
            } else {
                metric.setVoltage(round(219 + random.nextDouble() * 3, 2));
                metric.setCurrentA(0D);
                metric.setPowerKw(0D);
                metric.setTemperature(round(25 + random.nextDouble() * 10, 2));
                metric.setEnergyTotal(round(port.energyTotal, 4));
            }
            return metric;
        }
    }

    private void publishOrderEvent(String deviceNo, int portNo, String orderNo, String reason,
                                   double energyTotal, long ts) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("deviceId", deviceNo);
        event.put("portNo", portNo);
        event.put("orderNo", orderNo);
        event.put("stopReason", reason);
        event.put("energyTotal", round(energyTotal, 4));
        event.put("ts", ts);
        mqttGateway.publishUplink(deviceNo, "order_event", event);
    }

    private void publishAlert(Charger charger, int portNo, String faultType, double value, long now) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("deviceId", charger.getDeviceNo());
        alert.put("portNo", portNo);
        alert.put("type", faultType);
        alert.put("level", "WARN");
        alert.put("message", "端口温度超过阈值");
        alert.put("value", round(value, 2));
        alert.put("ts", now);
        mqttGateway.publishUplink(charger.getDeviceNo(), "alert", alert);
    }

    private double faultTemperature() {
        return round(68 + random.nextDouble() * 15, 2);
    }

    private SimConfig requireConfig() {
        SimConfig config = loadConfig();
        if (config == null) {
            throw new ServiceException("仿真配置不存在，请检查数据库初始化");
        }
        return config;
    }

    private SimConfig loadConfig() {
        // sim_config 主键是固定 id，租户拦截器只加条件不换行：取本租户的第一行，不能再写死 1
        List<SimConfig> rows = simConfigMapper.selectList(new LambdaQueryWrapper<SimConfig>()
                .orderByAsc(SimConfig::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 运行与否只认 sim_config：内存里的 running 只是本实例的观感，不能反过来决定能不能发报文。 */
    private boolean configEnabled() {
        SimConfig config = loadConfig();
        return config != null && config.getEnabled() != null && config.getEnabled() == 1;
    }

    private List<DeviceRuntime> sortedRuntimes() {
        long tenantId = TenantContext.get();
        return runtimes.values().stream()
                .filter(runtime -> runtime.tenantId == tenantId)
                .sorted(Comparator.comparing(runtime -> runtime.charger.getId()))
                .toList();
    }

    private double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }

    @PreDestroy
    public void shutdown() {
        states.forEach((tenantId, state) -> {
            if (!state.running) {
                return;
            }
            state.running = false;
            try {
                TenantContext.use(tenantId);
                publishOnline(false);
            } catch (Exception e) {
                log.debug("Unable to publish offline status during shutdown: {}", e.getMessage());
            } finally {
                TenantContext.clear();
            }
        });
    }

    private record PendingOrderEvent(String deviceNo, int portNo, String orderNo, String reason,
                                     double energyTotal, long ts) {
    }

    private record PortOrderStop(String orderNo, int portNo, double energyTotal, long ts) {
    }

    private static final class DeviceRuntime {
        private long tenantId;
        private Charger charger;
        private final Map<Integer, PortState> ports = new ConcurrentHashMap<>();
    }

    private static final class PortState {
        private int portNo;
        private String status = "IDLE";
        private String activeOrderNo;
        private double energyTotal;
        private double ratedPowerKw = 2.2D;
        private double overTempC = 120D;
        private double overCurrentA = 32D;
        private long startedAtMs;
        private long faultUntil;
        private String faultType = "OVER_TEMP";
        private boolean plugged;
        private boolean initialized;
    }
}
