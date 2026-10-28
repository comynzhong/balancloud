package com.balancloud.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Station;
import com.balancloud.device.dto.StationRequest;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.vo.StationOptionVo;
import com.balancloud.device.vo.StationStats;
import com.balancloud.device.vo.StationVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StationService {

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;
    private final StationScopeService scopeService;

    public StationService(StationMapper stationMapper,
                          ChargerMapper chargerMapper,
                          StationScopeService scopeService) {
        this.stationMapper = stationMapper;
        this.chargerMapper = chargerMapper;
        this.scopeService = scopeService;
    }

    public TableDataInfo list(String name, String status, long pageNum, long pageSize) {
        List<Long> stationIds = scopeService.authorizedStationIds();
        if (stationIds != null && stationIds.isEmpty()) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        Page<Station> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        if (stationIds != null) {
            wrapper.in(Station::getId, stationIds);
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(Station::getName, name.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Station::getStatus, status);
        }
        wrapper.orderByAsc(Station::getId);
        Page<Station> result = stationMapper.selectPage(page, wrapper);

        Map<Long, StationStats> stats = loadStats(result.getRecords());
        List<StationVo> rows = result.getRecords().stream()
                .map(station -> toVo(station, stats.get(station.getId())))
                .toList();
        return TableDataInfo.of(result.getTotal(), rows);
    }

    public StationVo detail(Long id) {
        Station station = requireStation(id);
        scopeService.assertAccess(station.getId());
        return toVo(station, loadStats(List.of(station)).get(station.getId()));
    }

    public List<StationOptionVo> options() {
        List<Long> stationIds = scopeService.authorizedStationIds();
        if (stationIds != null && stationIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<Station>()
                .eq(Station::getStatus, "ACTIVE")
                .orderByAsc(Station::getId);
        if (stationIds != null) {
            wrapper.in(Station::getId, stationIds);
        }
        return stationMapper.selectList(wrapper).stream()
                .map(station -> new StationOptionVo(station.getId(), station.getName()))
                .toList();
    }

    @Transactional
    public StationVo create(StationRequest request) {
        Station station = new Station();
        applyRequest(station, request);
        if (station.getStatus() == null) {
            station.setStatus("ACTIVE");
        }
        if (station.getCapacityKw() == null) {
            station.setCapacityKw(BigDecimal.ZERO);
        }
        station.setCreateBy(SecurityUtils.getUsername());
        stationMapper.insert(station);
        return toVo(station, null);
    }

    @Transactional
    public StationVo update(Long id, StationRequest request) {
        Station station = requireStation(id);
        scopeService.assertAccess(station.getId());
        applyRequest(station, request);
        if (station.getStatus() == null) {
            station.setStatus("ACTIVE");
        }
        station.setUpdateBy(SecurityUtils.getUsername());
        stationMapper.updateById(station);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Station station = requireStation(id);
        scopeService.assertAccess(station.getId());
        long chargerCount = chargerMapper.countByStationId(id);
        if (chargerCount > 0) {
            throw new ServiceException("场站下还有 " + chargerCount + " 台充电设备，请先删除设备");
        }
        stationMapper.deleteById(id);
    }

    private Station requireStation(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new ServiceException(404, "场站不存在");
        }
        return station;
    }

    private void applyRequest(Station station, StationRequest request) {
        station.setName(request.getName().trim());
        station.setAddress(request.getAddress());
        station.setLatitude(request.getLatitude());
        station.setLongitude(request.getLongitude());
        station.setCapacityKw(request.getCapacityKw());
        station.setManager(request.getManager());
        station.setPhone(request.getPhone());
        station.setStatus(request.getStatus());
        station.setRemark(request.getRemark());
    }

    private Map<Long, StationStats> loadStats(List<Station> stations) {
        if (stations.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> stationIds = stations.stream().map(Station::getId).toList();
        return chargerMapper.selectStatsByStationIds(stationIds).stream()
                .collect(Collectors.toMap(StationStats::getStationId, Function.identity()));
    }

    private StationVo toVo(Station station, StationStats stats) {
        StationVo vo = new StationVo();
        BeanUtils.copyProperties(station, vo);
        if (stats != null) {
            vo.setDeviceCount(stats.getDeviceCount());
            vo.setOnlineDeviceCount(stats.getOnlineDeviceCount());
            vo.setPortCount(stats.getPortCount());
            vo.setChargingPortCount(stats.getChargingPortCount());
            vo.setFaultPortCount(stats.getFaultPortCount());
        }
        return vo;
    }
}
