package com.balancloud.device.ocpp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.dto.OcppSimulatorRequest;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.ocpp.OcppProtocol;
import com.balancloud.device.ocpp.config.OcppProperties;
import com.balancloud.order.flow.AcCarOrderFlow;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 后台内置 OCPP 模拟桩：用 {@link ProcessBuilder} 起 {@code tools/ocpp-sim/sim.py}。
 *
 * <p>这是明确的命令执行面，边界只有三条：开关默认关闭、identity 必须命中当前租户已建档的
 * OCPP 设备、参数逐项放进参数列表不拼 shell。生产必须保持 {@code balan.ocpp.simulator.enabled=false}。
 */
@Service
public class OcppSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(OcppSimulatorService.class);
    private static final Pattern IDENTITY = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final Pattern VIN = Pattern.compile("^[A-Za-z0-9]{1,32}$");
    private static final Set<String> FAULTS = Set.of("none", "faulted", "disconnect");

    private final ChargerMapper chargerMapper;
    private final AppVehicleMapper vehicleMapper;
    private final OcppProperties properties;
    private final int serverPort;
    private final Map<String, RunningSim> running = new ConcurrentHashMap<>();

    public OcppSimulatorService(ChargerMapper chargerMapper,
                                AppVehicleMapper vehicleMapper,
                                OcppProperties properties,
                                @Value("${server.port:8080}") int serverPort) {
        this.chargerMapper = chargerMapper;
        this.vehicleMapper = vehicleMapper;
        this.properties = properties;
        this.serverPort = serverPort;
    }

    public Map<String, Object> status() {
        running.entrySet().removeIf(entry -> !entry.getValue().process().isAlive());
        List<Map<String, Object>> items = new ArrayList<>();
        for (RunningSim sim : running.values()) {
            items.add(Map.of(
                    "identity", sim.identity(),
                    "pid", sim.process().pid(),
                    "startedAt", sim.startedAt()));
        }
        return Map.of("enabled", properties.getSimulator().isEnabled(), "running", items);
    }

    public synchronized Map<String, Object> start(OcppSimulatorRequest request) {
        OcppProperties.Simulator simulator = properties.getSimulator();
        if (!simulator.isEnabled()) {
            throw new ServiceException(403,
                    "内置 OCPP 模拟器未开启（balan.ocpp.simulator.enabled=false），生产环境必须保持关闭");
        }
        String identity = request == null ? null : request.getIdentity();
        if (!StringUtils.hasText(identity) || !IDENTITY.matcher(identity).matches()) {
            throw new ServiceException(400, "identity 只能包含字母、数字、下划线和中划线，长度 1-32");
        }
        Charger charger = chargerMapper.selectByDeviceNo(identity);
        if (charger == null) {
            throw new ServiceException(400, "当前租户没有建档设备 " + identity);
        }
        if (!OcppProtocol.PROTOCOL.equalsIgnoreCase(charger.getProtocol())) {
            throw new ServiceException(400, "设备 " + identity + " 的协议不是 OCPP 1.6J");
        }
        RunningSim existing = running.get(identity);
        if (existing != null && existing.process().isAlive()) {
            throw new ServiceException(400, "模拟桩 " + identity + " 已在运行");
        }

        int connectors = intOrDefault(request.getConnectors(), 1, 1, 4, "connectors");
        Double powerKw = request.getPowerKw() == null
                ? null : doubleOrDefault(request.getPowerKw(), 120.0, 1, 1000, "powerKw");
        Integer meterStepWh = request.getMeterStepWh() == null
                ? null : intOrDefault(request.getMeterStepWh(), 200, 1, 1_000_000, "meterStepWh");
        int interval = intOrDefault(request.getIntervalSeconds(), 5, 1, 60, "intervalSeconds");
        int stopAfter = intOrDefault(request.getStopAfterSeconds(), 0, 0, 86_400, "stopAfterSeconds");
        String fault = StringUtils.hasText(request.getFault()) ? request.getFault().trim() : "none";
        if (!FAULTS.contains(fault)) {
            throw new ServiceException(400, "fault 只能是 none / faulted / disconnect");
        }
        boolean ac = AcCarOrderFlow.SCENE.equalsIgnoreCase(charger.getScene());
        String vin = resolveVin(request.getVin());

        Path script = Path.of(simulator.getScriptPath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new ServiceException(400, "模拟桩脚本不存在: " + script);
        }
        Path logFile = logFileOf(identity);
        try {
            Files.createDirectories(logFile.getParent());
            List<String> command = new ArrayList<>(List.of(
                    simulator.getPython(), script.toString(),
                    "--url", baseUrl(),
                    "--identity", identity,
                    "--mode", ac ? "ac" : "dc",
                    "--connectors", String.valueOf(connectors),
                    "--interval", String.valueOf(interval),
                    "--stop-after", String.valueOf(stopAfter),
                    "--fault", fault));
            if (powerKw != null) {
                command.add("--power-kw");
                command.add(String.valueOf(powerKw));
            }
            if (meterStepWh != null) {
                command.add("--meter-step-wh");
                command.add(String.valueOf(meterStepWh));
            }
            if (StartMethod.isPlugAndCharge(charger.getStartMethod())) {
                command.add("--plug-and-charge");
            }
            if (vin != null) {
                command.add("--vin");
                command.add(vin);
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            Process process = builder.start();
            if (process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                throw new ServiceException(500, "模拟桩启动失败，日志 " + logFile + "：" + tail(logFile));
            }
            RunningSim sim = new RunningSim(identity, process, Instant.now().toString());
            running.put(identity, sim);
            log.info("OCPP simulator started for {} (pid {})", identity, process.pid());
            return Map.of("identity", identity, "pid", process.pid(), "startedAt", sim.startedAt());
        } catch (IOException e) {
            throw new ServiceException(500, "模拟桩启动失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(500, "模拟桩启动被中断");
        }
    }

    public synchronized void stop(String identity) {
        if (!StringUtils.hasText(identity)) {
            throw new ServiceException(400, "identity 不能为空");
        }
        RunningSim sim = running.remove(identity);
        if (sim == null || !sim.process().isAlive()) {
            throw new ServiceException(400, "没有运行中的模拟桩 " + identity);
        }
        sim.process().destroy();
        try {
            if (!sim.process().waitFor(3, TimeUnit.SECONDS)) {
                sim.process().destroyForcibly();
            }
        } catch (InterruptedException e) {
            sim.process().destroyForcibly();
            Thread.currentThread().interrupt();
        }
        log.info("OCPP simulator stopped for {}", identity);
    }

    @PreDestroy
    public void shutdown() {
        for (RunningSim sim : running.values()) {
            sim.process().destroy();
        }
        running.clear();
    }

    /**
     * 即插即充的 idTag：请求显式给了就用请求值，否则取租户内第一辆填了 VIN 的车。
     * 都找不到就不传——桩会因 idTag 不是已登记 VIN 被平台拒绝，这是可观察的正确失败。
     */
    private String resolveVin(String requested) {
        if (StringUtils.hasText(requested)) {
            String value = requested.trim().toUpperCase(Locale.ROOT);
            if (!VIN.matcher(value).matches()) {
                throw new ServiceException(400, "vin 只能包含字母和数字，长度 1-32");
            }
            return value;
        }
        AppVehicle vehicle = vehicleMapper.selectOne(new LambdaQueryWrapper<AppVehicle>()
                .isNotNull(AppVehicle::getVin)
                .ne(AppVehicle::getVin, "")
                .orderByAsc(AppVehicle::getId)
                .last("LIMIT 1"));
        return vehicle == null ? null : vehicle.getVin();
    }

    private String baseUrl() {        String configured = properties.getSimulator().getUrl();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return "ws://127.0.0.1:" + serverPort;
    }

    private Path logFileOf(String identity) {
        return Path.of(System.getProperty("java.io.tmpdir"), "balan-ocpp-sim", identity + ".log");
    }

    private String tail(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - 5);
            String text = String.join(" | ", lines.subList(from, lines.size()));
            return text.length() > 500 ? text.substring(text.length() - 500) : text;
        } catch (IOException e) {
            return "（日志读取失败: " + e.getMessage() + "）";
        }
    }

    private int intOrDefault(Integer value, int fallback, int min, int max, String name) {
        int actual = value == null ? fallback : value;
        if (actual < min || actual > max) {
            throw new ServiceException(400, name + " 必须在 " + min + "-" + max + " 之间");
        }
        return actual;
    }

    private double doubleOrDefault(Double value, double fallback, double min, double max, String name) {
        double actual = value == null ? fallback : value;
        if (actual < min || actual > max) {
            throw new ServiceException(400, name + " 必须在 " + min + "-" + max + " 之间");
        }
        return actual;
    }

    private record RunningSim(String identity, Process process, String startedAt) {
    }
}
