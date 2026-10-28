package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.app.vo.AppChargerVo;
import com.balancloud.app.vo.AppPortVo;
import com.balancloud.app.vo.AppRatePeriodVo;
import com.balancloud.app.vo.AppRateVo;
import com.balancloud.app.vo.AppScanVo;
import com.balancloud.app.vo.AppStationDetailVo;
import com.balancloud.app.vo.AppStationVo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.ChargerPort;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.ChargerPortMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.vo.StationStats;
import com.balancloud.framework.geo.GeoDistance;
import com.balancloud.mqtt.DeviceShadowService;
import com.balancloud.mqtt.model.PortMetric;
import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.domain.RateStrategy;
import com.balancloud.rate.service.RateService;
import com.balancloud.rate.vo.EnabledRate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppStationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;
    private final ChargerPortMapper portMapper;
    private final DeviceShadowService shadowService;
    private final RateService rateService;

    public AppStationService(StationMapper stationMapper,
                             ChargerMapper chargerMapper,
                             ChargerPortMapper portMapper,
                             DeviceShadowService shadowService,
                             RateService rateService) {
        this.stationMapper = stationMapper;
        this.chargerMapper = chargerMapper;
        this.portMapper = portMapper;
        this.shadowService = shadowService;
        this.rateService = rateService;
    }

    /**
     * 带经纬度时按大圆距离升序，缺坐标的场站排在有距离的之后；不带则维持 id 升序（§55.3 距离上移）。
     */
    public List<AppStationVo> list(String keyword, Double latitude, Double longitude) {
        boolean located = latitude != null || longitude != null;
        if (located) {
            requireCoordinatePair(latitude, longitude);
        }
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<Station>()
                .eq(Station::getStatus, "ACTIVE")
                .orderByAsc(Station::getId);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(item -> item.like(Station::getName, value).or().like(Station::getAddress, value));
        }
        List<Station> stations = stationMapper.selectList(wrapper);
        if (stations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, StationStats> stats = chargerMapper.selectStatsByStationIds(
                        stations.stream().map(Station::getId).toList())
                .stream().collect(Collectors.toMap(StationStats::getStationId, Function.identity()));
        List<AppStationVo> vos = stations.stream().map(station -> {
            AppStationVo vo = new AppStationVo();
            fill(vo, station);
            applyStats(vo, stats.get(station.getId()));
            return vo;
        }).collect(Collectors.toCollection(ArrayList::new));
        if (located) {
            BigDecimal fromLat = BigDecimal.valueOf(latitude);
            BigDecimal fromLng = BigDecimal.valueOf(longitude);
            for (AppStationVo vo : vos) {
                vo.setDistanceMeters(GeoDistance.distanceMeters(fromLat, fromLng, vo.getLatitude(), vo.getLongitude()));
            }
            vos.sort(Comparator.comparing(AppStationVo::getDistanceMeters,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return vos;
    }

    private void requireCoordinatePair(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ServiceException(400, "经纬度需成对提供");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ServiceException(400, "经纬度超出范围");
        }
    }

    public AppStationDetailVo detail(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new ServiceException(404, "场站不存在");
        }
        List<Charger> chargers = chargerMapper.selectList(new LambdaQueryWrapper<Charger>()
                .eq(Charger::getStationId, id)
                .orderByAsc(Charger::getId));
        List<AppChargerVo> devices = chargers.stream().map(this::toChargerVo).toList();

        AppStationDetailVo vo = new AppStationDetailVo();
        fill(vo, station);
        vo.setDeviceCount(devices.size());
        vo.setOnlineDeviceCount(devices.stream().filter(item -> "ONLINE".equals(item.getOnlineStatus())).count());
        vo.setPortCount(devices.stream().mapToLong(AppChargerVo::getPortCount).sum());
        vo.setIdlePortCount(devices.stream().mapToLong(AppChargerVo::getIdlePorts).sum());
        vo.setChargingPortCount(devices.stream().mapToLong(AppChargerVo::getChargingPorts).sum());
        vo.setFaultPortCount(devices.stream().mapToLong(AppChargerVo::getFaultPorts).sum());
        vo.setRate(rateFor(id));
        vo.setDevices(devices);
        return vo;
    }

    public AppRateVo rateFor(Long stationId) {
        AppRateVo vo = new AppRateVo();
        vo.setConfigured(false);
        vo.setPeriods(List.of());
        EnabledRate enabled;
        try {
            enabled = rateService.requireEnabled(stationId);
        } catch (ServiceException e) {
            return vo;
        }
        RateStrategy strategy = enabled.getStrategy();
        vo.setConfigured(true);
        vo.setStrategyId(strategy.getId());
        vo.setStrategyName(strategy.getName());
        vo.setBillingMode(strategy.getBillingMode());
        List<RatePeriod> rawPeriods = enabled.getPeriods();
        List<AppRatePeriodVo> periods = rawPeriods.stream().map(this::toPeriodVo).toList();
        vo.setPeriods(periods);
        LocalTime now = LocalTime.now(ZONE);
        for (int i = 0; i < rawPeriods.size(); i++) {
            if (inPeriod(now, rawPeriods.get(i))) {
                vo.setCurrent(periods.get(i));
                break;
            }
        }
        return vo;
    }

    public AppScanVo scan(String deviceNo) {
        Charger charger = chargerMapper.selectByDeviceNo(deviceNo.trim());
        if (charger == null) {
            throw new ServiceException(404, "设备不存在，请检查设备编号");
        }
        Station station = stationMapper.selectById(charger.getStationId());
        if (station == null) {
            throw new ServiceException(404, "设备未绑定有效场站");
        }
        AppChargerVo chargerVo = toChargerVo(charger);
        AppScanVo vo = new AppScanVo();
        vo.setStationId(station.getId());
        vo.setStationName(station.getName());
        vo.setStationAddress(station.getAddress());
        vo.setChargerId(charger.getId());
        vo.setDeviceNo(charger.getDeviceNo());
        vo.setChargerName(charger.getName());
        vo.setOnlineStatus(charger.getOnlineStatus());
        vo.setStartMethod(charger.getStartMethod());
        vo.setPortCount(charger.getPortCount());
        vo.setPorts(chargerVo.getPorts());
        vo.setRate(rateFor(station.getId()));
        return vo;
    }

    public List<AppPortVo> portsOf(Long chargerId) {
        Charger charger = chargerMapper.selectById(chargerId);
        if (charger == null) {
            throw new ServiceException(404, "设备不存在");
        }
        return toPortVos(charger, portMapper.selectByChargerId(chargerId));
    }

    private AppChargerVo toChargerVo(Charger charger) {
        List<AppPortVo> ports = toPortVos(charger, portMapper.selectByChargerId(charger.getId()));
        AppChargerVo vo = new AppChargerVo();
        vo.setId(charger.getId());
        vo.setDeviceNo(charger.getDeviceNo());
        vo.setName(charger.getName());
        vo.setPortCount(charger.getPortCount());
        vo.setOnlineStatus(charger.getOnlineStatus());
        vo.setStartMethod(charger.getStartMethod());
        vo.setPorts(ports);
        vo.setChargingPorts(ports.stream().filter(port -> "CHARGING".equals(port.getStatus())).count());
        vo.setFaultPorts(ports.stream().filter(port -> "FAULT".equals(port.getStatus())).count());
        vo.setIdlePorts(Math.max(ports.size() - vo.getChargingPorts() - vo.getFaultPorts(), 0));
        return vo;
    }

    private List<AppPortVo> toPortVos(Charger charger, List<ChargerPort> ports) {
        Map<Integer, PortMetric> shadow = shadowService.get(charger.getDeviceNo());
        return ports.stream().map(port -> {
            AppPortVo vo = new AppPortVo();
            vo.setPortNo(port.getPortNo());
            vo.setStatus(port.getStatus());
            vo.setRatedCurrentA(port.getRatedCurrentA());
            vo.setRatedPowerKw(port.getRatedPowerKw());
            PortMetric metric = shadow.get(port.getPortNo());
            if (metric != null) {
                vo.setVoltage(metric.getVoltage());
                vo.setCurrentA(metric.getCurrentA());
                vo.setTemperature(metric.getTemperature());
                vo.setPowerKw(metric.getPowerKw());
                vo.setMetricTs(metric.getTs());
            }
            return vo;
        }).toList();
    }

    private AppRatePeriodVo toPeriodVo(RatePeriod period) {
        AppRatePeriodVo vo = new AppRatePeriodVo();
        vo.setPeriodType(period.getPeriodType());
        vo.setPeriodLabel(periodLabel(period.getPeriodType()));
        vo.setStartTime(period.getStartTime().toString());
        vo.setEndTime(period.getEndTime().toString());
        vo.setTimePrice(period.getTimePrice());
        vo.setEnergyPrice(period.getEnergyPrice());
        vo.setServicePrice(period.getServicePrice());
        vo.setServiceUnit(period.getServiceUnit());
        return vo;
    }

    private boolean inPeriod(LocalTime now, RatePeriod period) {
        LocalTime start = period.getStartTime();
        LocalTime end = period.getEndTime();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private String periodLabel(String type) {
        return switch (type == null ? "" : type) {
            case "PEAK" -> "峰";
            case "FLAT" -> "平";
            case "VALLEY" -> "谷";
            default -> type;
        };
    }

    private void fill(AppStationVo vo, Station station) {
        vo.setId(station.getId());
        vo.setName(station.getName());
        vo.setAddress(station.getAddress());
        vo.setLatitude(station.getLatitude());
        vo.setLongitude(station.getLongitude());
        vo.setPhone(station.getPhone());
        vo.setManager(station.getManager());
        vo.setStatus(station.getStatus());
    }

    private void applyStats(AppStationVo vo, StationStats stats) {
        if (stats == null) {
            return;
        }
        vo.setDeviceCount(stats.getDeviceCount());
        vo.setOnlineDeviceCount(stats.getOnlineDeviceCount());
        vo.setPortCount(stats.getPortCount());
        vo.setChargingPortCount(stats.getChargingPortCount());
        vo.setFaultPortCount(stats.getFaultPortCount());
        vo.setIdlePortCount(Math.max(stats.getPortCount() - stats.getChargingPortCount()
                - stats.getFaultPortCount(), 0));
    }
}
