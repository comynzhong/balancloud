package com.balancloud.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.StartMethod;
import com.balancloud.device.domain.Station;
import com.balancloud.device.dto.ChargerRequest;
import com.balancloud.device.dto.SimBatchRequest;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.ocpp.OcppProtocol;
import com.balancloud.device.protocol.ProtocolAdapterRegistry;
import com.balancloud.device.vo.ChargerDetailVo;
import com.balancloud.device.vo.ChargerVo;
import com.balancloud.device.vo.PortVo;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.metric.MetricHistoryPointVo;
import com.balancloud.metric.TdengineClient;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.flow.AcCarOrderFlow;
import com.balancloud.order.flow.OrderFlow;
import com.balancloud.order.flow.OrderFlowRegistry;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChargerService {

    private static final BigDecimal DEFAULT_VOLTAGE = new BigDecimal("220.00");
    private static final BigDecimal DEFAULT_POWER_KW = new BigDecimal("2.200");
    private static final BigDecimal DEFAULT_OVER_TEMP = new BigDecimal("60.00");
    private static final BigDecimal DEFAULT_OVER_CURRENT = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_RATED_CURRENT = new BigDecimal("10.00");

    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;
    private final DeviceShadowService shadowService;
    private final TdengineClient tdengineClient;
    private final ChargeOrderMapper orderMapper;
    private final AppUserMapper appUserMapper;
    private final AppVehicleMapper vehicleMapper;
    private final SensitiveDataMasker masker;
    private final OrderFlowRegistry orderFlowRegistry;
    private final ProtocolAdapterRegistry protocolAdapterRegistry;

    public ChargerService(ChargerMapper chargerMapper,
                          ChargerPortMapper portMapper,
                          StationMapper stationMapper,
                          StationScopeService scopeService,
                          DeviceShadowService shadowService,
                          TdengineClient tdengineClient,
                          ChargeOrderMapper orderMapper,
                          AppUserMapper appUserMapper,
                          AppVehicleMapper vehicleMapper,
                          SensitiveDataMasker masker,
                          OrderFlowRegistry orderFlowRegistry,
                          ProtocolAdapterRegistry protocolAdapterRegistry) {
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
        this.shadowService = shadowService;
        this.tdengineClient = tdengineClient;
        this.orderMapper = orderMapper;
        this.appUserMapper = appUserMapper;
        this.vehicleMapper = vehicleMapper;
        this.masker = masker;
        this.orderFlowRegistry = orderFlowRegistry;
        this.protocolAdapterRegistry = protocolAdapterRegistry;
    }

    public TableDataInfo list(Long stationId, String keyword, String onlineStatus,
                              String groupName, long pageNum, long pageSize) {
        List<Long> scope = scopeService.authorizedStationIds();
        if (stationId != null) {
            scopeService.assertAccess(stationId);
        }
        if (scope != null && scope.isEmpty()) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        Page<Charger> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<Charger> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(Charger::getStationId, scope);
        }
        if (stationId != null) {
            wrapper.eq(Charger::getStationId, stationId);
        }
        if (StringUtils.hasText(onlineStatus)) {
            wrapper.eq(Charger::getOnlineStatus, onlineStatus);
        }
        if (StringUtils.hasText(groupName)) {
            wrapper.eq(Charger::getGroupName, groupName.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(Charger::getDeviceNo, value).or().like(Charger::getName, value));
        }
        wrapper.orderByAsc(Charger::getId);
        Page<Charger> result = chargerMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return TableDataInfo.of(result.getTotal(), Collections.emptyList());
        }

        List<Long> chargerIds = result.getRecords().stream().map(Charger::getId).toList();
        Map<Long, List<ChargerPort>> portsByCharger = loadPorts(chargerIds);
        Map<Long, String> stationNames = loadStationNames(result.getRecords());
        List<ChargerVo> rows = result.getRecords().stream()
                .map(charger -> toVo(charger, stationNames.get(charger.getStationId()),
                        portsByCharger.getOrDefault(charger.getId(), List.of())))
                .toList();
        return TableDataInfo.of(result.getTotal(), rows);
    }

    public ChargerDetailVo detail(Long id) {
        Charger charger = requireCharger(id);
        scopeService.assertAccess(charger.getStationId());
        Map<Long, String> stationNames = loadStationNames(List.of(charger));
        List<ChargerPort> ports = portMapper.selectByChargerId(charger.getId());
        return new ChargerDetailVo(
                toVo(charger, stationNames.get(charger.getStationId()), ports),
                toPortVos(charger, ports)
        );
    }

    public List<String> groups() {
        List<Long> scope = scopeService.authorizedStationIds();
        if (scope != null && scope.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<Charger> wrapper = new QueryWrapper<Charger>()
                .select("DISTINCT group_name")
                .isNotNull("group_name")
                .ne("group_name", "");
        if (scope != null) {
            wrapper.in("station_id", scope);
        }
        return chargerMapper.selectObjs(wrapper).stream()
                .map(String::valueOf)
                .sorted()
                .toList();
    }

    /**
     * 建档下拉 + 展示裁剪的数据源：只列已注册场景，附带该场景的启动时序差异。
     * 前端据此决定「握手阶段」这类端口列要不要出现，而不是自己维护一份场景白名单。
     */
    public List<Map<String, Object>> scenes() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String scene : orderFlowRegistry.available()) {
            OrderFlow flow = orderFlowRegistry.require(scene);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scene", scene);
            row.put("startsOnCommandAck", flow.startsOnCommandAck());
            row.put("preChargePhases", flow.preChargePhases().stream().map(Enum::name).toList());
            row.put("stoppingTimeoutSeconds", flow.stoppingTimeout().getSeconds());
            rows.add(row);
        }
        return rows;
    }

    public List<PortVo> ports(Long id) {
        Charger charger = requireCharger(id);
        scopeService.assertAccess(charger.getStationId());
        return toPortVos(charger, portMapper.selectByChargerId(charger.getId()));
    }

    public List<MetricHistoryPointVo> history(Long id, Integer portNo, int minutes, int limit) {
        Charger charger = requireCharger(id);
        scopeService.assertAccess(charger.getStationId());
        if (portNo == null || portNo < 1 || portNo > charger.getPortCount()) {
            throw new ServiceException(400, "端口号不合法");
        }
        return tdengineClient.queryHistory(charger.getId(), portNo, minutes, limit);
    }

    @Transactional
    public ChargerVo create(ChargerRequest request) {
        Station station = requireStation(request.getStationId());
        scopeService.assertAccess(station.getId());
        if (!"ACTIVE".equals(station.getStatus())) {
            throw new ServiceException(400, "场站已停用，无法新增设备");
        }
        if (chargerMapper.countByDeviceNoIncludingDeleted(request.getDeviceNo()) > 0) {
            throw new ServiceException(400, "设备编号已存在（含已删除设备）");
        }
        Charger charger = buildCharger(request, 0);
        charger.setCreateBy(SecurityUtils.getUsername());
        chargerMapper.insert(charger);
        createPorts(charger);
        return detail(charger.getId()).getCharger();
    }

    @Transactional
    public ChargerVo update(Long id, ChargerRequest request) {
        Charger charger = requireCharger(id);
        scopeService.assertAccess(charger.getStationId());
        Station station = requireStation(request.getStationId());
        scopeService.assertAccess(station.getId());
        if (!charger.getDeviceType().equals(request.getDeviceType())) {
            throw new ServiceException(400, "设备类型创建后不可修改，请新建设备");
        }
        if (chargerMapper.countByDeviceNoExcludingId(request.getDeviceNo(), id) > 0) {
            throw new ServiceException(400, "设备编号已存在（含已删除设备）");
        }
        boolean stationChanged = !charger.getStationId().equals(request.getStationId());
        if (stationChanged && portMapper.countChargingByChargerId(id) > 0) {
            throw new ServiceException(400, "设备存在充电中的端口，暂不能迁移场站");
        }
        String previousDeviceNo = charger.getDeviceNo();

        applyRequest(charger, request);
        charger.setUpdateBy(SecurityUtils.getUsername());
        chargerMapper.updateById(charger);
        // MyBatis-Plus 默认忽略 null 字段，清空分组需显式置空
        if (charger.getGroupName() == null) {
            chargerMapper.update(null, new LambdaUpdateWrapper<Charger>()
                    .eq(Charger::getId, id)
                    .set(Charger::getGroupName, null));
        }
        if (!previousDeviceNo.equals(charger.getDeviceNo())) {
            shadowService.delete(previousDeviceNo);
        }
        portMapper.update(null, new LambdaUpdateWrapper<ChargerPort>()
                .eq(ChargerPort::getChargerId, id)
                .set(ChargerPort::getRatedPowerKw, charger.getRatedPowerKw()));
        return detail(id).getCharger();
    }

    @Transactional
    public void delete(Long id) {
        Charger charger = requireCharger(id);
        scopeService.assertAccess(charger.getStationId());
        if (portMapper.countChargingByChargerId(id) > 0) {
            throw new ServiceException(400, "设备存在充电中的端口，暂不能删除");
        }
        portMapper.deleteByChargerId(id);
        chargerMapper.deleteById(id);
        shadowService.delete(charger.getDeviceNo());
    }

    @Transactional
    public List<ChargerVo> createSimulatedBatch(SimBatchRequest request) {
        Station station = requireStation(request.getStationId());
        scopeService.assertAccess(station.getId());
        String prefix = StringUtils.hasText(request.getNamePrefix()) ? request.getNamePrefix().trim() : "仿真设备";
        String typeTag = request.getDeviceType().replace("SOCKET_", "S").replace("DC_", "D");
        long batchSeed = System.currentTimeMillis() % 100_000;
        List<ChargerVo> created = new ArrayList<>(request.getCount());
        for (int i = 1; i <= request.getCount(); i++) {
            String deviceNo = "SIM-" + typeTag + "-" + batchSeed + "-" + i;
            int suffix = i;
            while (chargerMapper.countByDeviceNoIncludingDeleted(deviceNo) > 0) {
                deviceNo = "SIM-" + typeTag + "-" + batchSeed + "-" + (++suffix);
            }
            ChargerRequest chargerRequest = new ChargerRequest();
            chargerRequest.setStationId(station.getId());
            chargerRequest.setDeviceNo(deviceNo);
            chargerRequest.setName(prefix + "-" + i);
            chargerRequest.setDeviceType(request.getDeviceType());
            chargerRequest.setProtocol("BALAN_MQTT");
            chargerRequest.setFirmwareVersion("v-sim");
            chargerRequest.setRatedVoltage(DEFAULT_VOLTAGE);
            chargerRequest.setRatedPowerKw(DEFAULT_POWER_KW);
            chargerRequest.setOverTempC(DEFAULT_OVER_TEMP);
            chargerRequest.setOverCurrentA(DEFAULT_OVER_CURRENT);
            chargerRequest.setIsSimulated(1);

            Charger charger = buildCharger(chargerRequest, 1);
            charger.setCreateBy(SecurityUtils.getUsername());
            chargerMapper.insert(charger);
            createPorts(charger);
            created.add(toVo(charger, station.getName(), portMapper.selectByChargerId(charger.getId())));
        }
        return created;
    }

    private Charger buildCharger(ChargerRequest request, int defaultSimulated) {
        Charger charger = new Charger();
        charger.setStationId(request.getStationId());
        charger.setDeviceNo(request.getDeviceNo().trim());
        charger.setName(request.getName().trim());
        charger.setGroupName(normalizeGroup(request.getGroupName()));
        String protocol = normalizeProtocol(request.getProtocol());
        String scene = normalizeScene(request.getScene());
        String startMethod = normalizeStartMethod(request.getStartMethod());
        // buildCharger 是建档的唯一入口（含批量仿真），组合校验放这里一次覆盖全部建法
        validateCombination(request.getDeviceType(), protocol, scene, startMethod);
        charger.setDeviceType(request.getDeviceType());
        charger.setPortCount(portCountOf(request.getDeviceType()));
        charger.setProtocol(protocol);
        charger.setScene(scene);
        charger.setStartMethod(startMethod);
        charger.setFirmwareVersion(request.getFirmwareVersion());
        charger.setRatedVoltage(request.getRatedVoltage() == null ? DEFAULT_VOLTAGE : request.getRatedVoltage());
        charger.setRatedPowerKw(request.getRatedPowerKw() == null ? DEFAULT_POWER_KW : request.getRatedPowerKw());
        charger.setOverTempC(request.getOverTempC() == null ? DEFAULT_OVER_TEMP : request.getOverTempC());
        charger.setOverCurrentA(request.getOverCurrentA() == null ? DEFAULT_OVER_CURRENT : request.getOverCurrentA());
        charger.setOnlineStatus("OFFLINE");
        charger.setIsSimulated(request.getIsSimulated() == null ? defaultSimulated : request.getIsSimulated());
        charger.setRemark(request.getRemark());
        return charger;
    }

    private void applyRequest(Charger charger, ChargerRequest request) {
        charger.setStationId(request.getStationId());
        charger.setDeviceNo(request.getDeviceNo().trim());
        charger.setName(request.getName().trim());
        charger.setGroupName(normalizeGroup(request.getGroupName()));
        // 编辑时允许不带 scene / protocol / startMethod：不传即保持原值，
        // 避免旧客户端把车桩静默改回两轮、把 OCPP 桩改回内置协议。
        String scene = StringUtils.hasText(request.getScene())
                ? normalizeScene(request.getScene()) : charger.getScene();
        String protocol = StringUtils.hasText(request.getProtocol())
                ? normalizeProtocol(request.getProtocol()) : charger.getProtocol();
        String startMethod = StringUtils.hasText(request.getStartMethod())
                ? normalizeStartMethod(request.getStartMethod()) : normalizeStartMethod(charger.getStartMethod());
        validateCombination(charger.getDeviceType(), protocol, scene, startMethod);
        charger.setScene(scene);
        charger.setProtocol(protocol);
        charger.setStartMethod(startMethod);
        charger.setFirmwareVersion(request.getFirmwareVersion());
        charger.setRatedVoltage(request.getRatedVoltage() == null ? DEFAULT_VOLTAGE : request.getRatedVoltage());
        charger.setRatedPowerKw(request.getRatedPowerKw() == null ? DEFAULT_POWER_KW : request.getRatedPowerKw());
        charger.setOverTempC(request.getOverTempC() == null ? DEFAULT_OVER_TEMP : request.getOverTempC());
        charger.setOverCurrentA(request.getOverCurrentA() == null ? DEFAULT_OVER_CURRENT : request.getOverCurrentA());
        charger.setIsSimulated(request.getIsSimulated() == null ? charger.getIsSimulated() : request.getIsSimulated());
        charger.setRemark(request.getRemark());
    }

    private void createPorts(Charger charger) {
        for (int portNo = 1; portNo <= charger.getPortCount(); portNo++) {
            ChargerPort port = new ChargerPort();
            port.setChargerId(charger.getId());
            port.setPortNo(portNo);
            port.setRatedCurrentA(DEFAULT_RATED_CURRENT);
            port.setRatedPowerKw(charger.getRatedPowerKw());
            port.setStatus("IDLE");
            portMapper.insert(port);
        }
    }

    private String normalizeGroup(String groupName) {
        if (!StringUtils.hasText(groupName)) {
            return null;
        }
        return groupName.trim();
    }

    private String normalizeScene(String scene) {
        String value = StringUtils.hasText(scene)
                ? scene.trim().toUpperCase()
                : OrderFlowRegistry.DEFAULT_SCENE;
        if (orderFlowRegistry.find(value).isEmpty()) {
            throw new ServiceException(400, "场景不合法: " + value
                    + "，已注册场景 " + orderFlowRegistry.available());
        }
        return value;
    }

    private String normalizeProtocol(String protocol) {
        String value = StringUtils.hasText(protocol) ? protocol.trim().toUpperCase() : "BALAN_MQTT";
        if (protocolAdapterRegistry.find(value).isEmpty()) {
            throw new ServiceException(400, "协议不合法: " + value
                    + "，已注册协议 " + protocolAdapterRegistry.available());
        }
        return value;
    }

    private int portCountOf(String deviceType) {
        return switch (deviceType) {
            case "SOCKET_2" -> 2;
            case "SOCKET_10" -> 10;
            case "SOCKET_12" -> 12;
            // 车桩按枪数建档；OCPP 的 connectorId 与端口号一一对应（1 起）
            case "DC_SINGLE" -> 1;
            case "DC_DUAL" -> 2;
            case "AC_SINGLE" -> 1;
            case "AC_DUAL" -> 2;
            default -> throw new ServiceException(400, "设备类型不合法");
        };
    }

    private String normalizeStartMethod(String startMethod) {
        String value = StringUtils.hasText(startMethod) ? startMethod.trim().toUpperCase() : StartMethod.SCAN;
        if (!StartMethod.SCAN.equals(value) && !StartMethod.PLUG_AND_CHARGE.equals(value)) {
            throw new ServiceException(400, "启动方式不合法: " + value + "，可选 " + StartMethod.SCAN
                    + " / " + StartMethod.PLUG_AND_CHARGE);
        }
        return value;
    }

    /**
     * 设备类型是端口数工厂，场景是业务时序，启动方式是设备属性，三者必须自洽。
     * 只在这里拦一次：建档与编辑都走 buildCharger / applyRequest。
     */
    private void validateCombination(String deviceType, String protocol, String scene, String startMethod) {
        String expectedScene = switch (deviceType) {
            case "SOCKET_2", "SOCKET_10", "SOCKET_12" -> "TWOWHEEL";
            case "DC_SINGLE", "DC_DUAL" -> "DC_CAR";
            case "AC_SINGLE", "AC_DUAL" -> AcCarOrderFlow.SCENE;
            default -> throw new ServiceException(400, "设备类型不合法");
        };
        if (!expectedScene.equals(scene)) {
            throw new ServiceException(400, "设备类型 " + deviceType + " 只能用于场景 " + expectedScene
                    + "，当前场景 " + scene);
        }
        if (AcCarOrderFlow.SCENE.equals(scene) && !OcppProtocol.PROTOCOL.equals(protocol)) {
            throw new ServiceException(400, "交流桩场景 " + AcCarOrderFlow.SCENE
                    + " 目前只支持协议 " + OcppProtocol.PROTOCOL + "，当前协议 " + protocol);
        }
        if (StartMethod.PLUG_AND_CHARGE.equals(startMethod) && !AcCarOrderFlow.SCENE.equals(scene)) {
            throw new ServiceException(400, "插枪即充只适用于交流桩场景 " + AcCarOrderFlow.SCENE
                    + "，当前场景 " + scene);
        }
    }

    private Charger requireCharger(Long id) {
        Charger charger = chargerMapper.selectById(id);
        if (charger == null) {
            throw new ServiceException(404, "设备不存在");
        }
        return charger;
    }

    private Station requireStation(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new ServiceException(404, "归属场站不存在");
        }
        return station;
    }

    private Map<Long, List<ChargerPort>> loadPorts(List<Long> chargerIds) {
        if (chargerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return portMapper.selectList(new LambdaQueryWrapper<ChargerPort>()
                        .in(ChargerPort::getChargerId, chargerIds)
                        .orderByAsc(ChargerPort::getPortNo))
                .stream()
                .collect(Collectors.groupingBy(ChargerPort::getChargerId));
    }

    private Map<Long, String> loadStationNames(List<Charger> chargers) {
        List<Long> stationIds = chargers.stream().map(Charger::getStationId).distinct().toList();
        if (stationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return stationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName));
    }

    private ChargerVo toVo(Charger charger, String stationName, List<ChargerPort> ports) {
        Map<Integer, PortMetric> shadow = shadowService.get(charger.getDeviceNo());
        ChargerVo vo = new ChargerVo();
        BeanUtils.copyProperties(charger, vo);
        vo.setStationName(stationName);
        long charging = ports.stream().filter(port -> "CHARGING".equals(port.getStatus())).count();
        long fault = ports.stream().filter(port -> "FAULT".equals(port.getStatus())).count();
        vo.setChargingPorts(charging);
        vo.setFaultPorts(fault);
        vo.setIdlePorts(Math.max(ports.size() - charging - fault, 0));
        double power = shadow.values().stream()
                .filter(metric -> metric.getPowerKw() != null)
                .mapToDouble(PortMetric::getPowerKw)
                .sum();
        double maxTemperature = shadow.values().stream()
                .filter(metric -> metric.getTemperature() != null)
                .mapToDouble(PortMetric::getTemperature)
                .max()
                .orElse(0D);
        vo.setCurrentPowerKw(Math.round(power * 1000D) / 1000D);
        vo.setMaxTemperature(maxTemperature);
        return vo;
    }

    private List<PortVo> toPortVos(Charger charger, List<ChargerPort> ports) {
        Map<Integer, PortMetric> shadow = shadowService.get(charger.getDeviceNo());
        Map<String, ChargeOrder> orders = loadActiveOrders(ports);
        Map<Long, AppUser> users = loadUsers(orders.values());
        Map<Long, AppVehicle> vehicles = loadVehicles(orders.values());
        return ports.stream().map(port -> {
            PortVo vo = new PortVo();
            BeanUtils.copyProperties(port, vo);
            vo.setOrderNo(port.getCurrentOrderNo());
            ChargeOrder order = orders.get(port.getCurrentOrderNo());
            if (order != null) {
                vo.setOrderStartTime(order.getStartTime());
                vo.setOrderDurationSeconds(order.getDurationSeconds());
                AppUser user = users.get(order.getAppUserId());
                if (user != null) {
                    vo.setAppUserId(user.getId());
                    vo.setAppUserNickname(user.getNickname());
                    vo.setAppUserPhone(masker.displayPhone(user.getPhone()));
                }
                AppVehicle vehicle = vehicles.get(order.getVehicleId());
                if (vehicle != null) {
                    vo.setVehiclePlateNo(masker.displayPlate(vehicle.getPlateNo()));
                }
            }
            PortMetric metric = shadow.get(port.getPortNo());
            if (metric != null) {
                vo.setVoltage(metric.getVoltage());
                vo.setCurrentA(metric.getCurrentA());
                vo.setTemperature(metric.getTemperature());
                vo.setPowerKw(metric.getPowerKw());
                vo.setEnergyTotal(metric.getEnergyTotal());
                vo.setSignalStrength(metric.getSignalStrength());
                vo.setMetricTs(metric.getTs());
            }
            return vo;
        }).toList();
    }

    private Map<String, ChargeOrder> loadActiveOrders(List<ChargerPort> ports) {
        List<String> orderNos = ports.stream().map(ChargerPort::getCurrentOrderNo)
                .filter(StringUtils::hasText).toList();
        if (orderNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<ChargeOrder>()
                        .in(ChargeOrder::getOrderNo, orderNos)
                        .in(ChargeOrder::getStatus, OrderStatus.PENDING_START.name(),
                                OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name()))
                .stream().collect(Collectors.toMap(ChargeOrder::getOrderNo, item -> item, (a, b) -> a));
    }

    private Map<Long, AppUser> loadUsers(java.util.Collection<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getAppUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, item -> item));
    }

    private Map<Long, AppVehicle> loadVehicles(java.util.Collection<ChargeOrder> orders) {
        List<Long> ids = orders.stream().map(ChargeOrder::getVehicleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return vehicleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppVehicle::getId, item -> item));
    }
}
