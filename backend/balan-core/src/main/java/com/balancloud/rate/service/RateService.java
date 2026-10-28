package com.balancloud.rate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.rate.domain.RatePeriod;
import com.balancloud.rate.domain.RateStrategy;
import com.balancloud.rate.dto.RatePeriodRequest;
import com.balancloud.rate.dto.RateStrategyRequest;
import com.balancloud.rate.mapper.RatePeriodMapper;
import com.balancloud.rate.mapper.RateStrategyMapper;
import com.balancloud.rate.model.BillingMode;
import com.balancloud.rate.model.PeriodType;
import com.balancloud.rate.model.ServiceUnit;
import com.balancloud.rate.model.StrategyType;
import com.balancloud.rate.vo.EnabledRate;
import com.balancloud.rate.vo.RatePeriodVo;
import com.balancloud.rate.vo.RatePlan;
import com.balancloud.rate.vo.RateStrategyVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RateService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final RateStrategyMapper strategyMapper;
    private final RatePeriodMapper periodMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;

    public RateService(RateStrategyMapper strategyMapper,
                       RatePeriodMapper periodMapper,
                       StationMapper stationMapper,
                       StationScopeService scopeService) {
        this.strategyMapper = strategyMapper;
        this.periodMapper = periodMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
    }

    public TableDataInfo list(Long stationId, String keyword, Integer enabled, String strategyType,
                              long pageNum, long pageSize) {
        List<Long> scope = scopeService.authorizedStationIds();
        if (stationId != null) {
            scopeService.assertAccess(stationId);
        }
        if (scope != null && scope.isEmpty()) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        Page<RateStrategy> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<RateStrategy> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(RateStrategy::getStationId, scope);
        }
        if (stationId != null) {
            wrapper.eq(RateStrategy::getStationId, stationId);
        }
        if (enabled != null) {
            wrapper.eq(RateStrategy::getEnabled, enabled);
        }
        if (StringUtils.hasText(strategyType)) {
            wrapper.eq(RateStrategy::getStrategyType, parseType(strategyType).name());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(RateStrategy::getName, keyword.trim());
        }
        wrapper.orderByDesc(RateStrategy::getEnabled).orderByDesc(RateStrategy::getId);
        Page<RateStrategy> result = strategyMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return TableDataInfo.of(result.getTotal(), Collections.emptyList());
        }
        Map<Long, List<RatePeriod>> periods = loadPeriods(
                result.getRecords().stream().map(RateStrategy::getId).toList());
        Map<Long, String> stationNames = stationNames(result.getRecords());
        LocalDate today = LocalDate.now();
        List<RateStrategyVo> rows = result.getRecords().stream()
                .map(strategy -> toVo(strategy, stationNames.get(strategy.getStationId()),
                        periods.getOrDefault(strategy.getId(), List.of()), today))
                .toList();
        return TableDataInfo.of(result.getTotal(), rows);
    }

    public RateStrategyVo detail(Long id) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        return toVo(strategy, stationNames(List.of(strategy)).get(strategy.getStationId()),
                periodsFor(id), LocalDate.now());
    }

    public List<RatePeriodVo> periods(Long id) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        return periodsFor(id).stream().map(this::toPeriodVo).toList();
    }

    @Transactional
    public RateStrategyVo create(RateStrategyRequest request) {
        Station station = requireStation(request.getStationId());
        scopeService.assertAccess(station.getId());
        if (!"ACTIVE".equals(station.getStatus())) {
            throw new ServiceException(400, "场站已停用，无法新增费率策略");
        }
        BillingMode mode = parseMode(request.getBillingMode());
        StrategyType type = parseType(request.getStrategyType());
        validateEffectiveRange(request.getEffectiveFrom(), request.getEffectiveTo());
        List<RatePeriod> periods = validateAndBuild(request.getPeriods(), type);

        RateStrategy strategy = new RateStrategy();
        strategy.setStationId(station.getId());
        strategy.setName(request.getName().trim());
        strategy.setBillingMode(type == StrategyType.SERVICE ? BillingMode.MIXED.name() : mode.name());
        strategy.setStrategyType(type.name());
        strategy.setEffectiveFrom(request.getEffectiveFrom());
        strategy.setEffectiveTo(request.getEffectiveTo());
        strategy.setEnabled(0);
        strategy.setRemark(request.getRemark());
        strategy.setCreateBy(SecurityUtils.getUsername());
        strategyMapper.insert(strategy);
        savePeriods(strategy.getId(), periods);
        return detail(strategy.getId());
    }

    @Transactional
    public RateStrategyVo update(Long id, RateStrategyRequest request) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        Station station = requireStation(request.getStationId());
        scopeService.assertAccess(station.getId());
        if (!"ACTIVE".equals(station.getStatus())) {
            throw new ServiceException(400, "场站已停用，无法修改费率策略");
        }
        if (!strategy.getStationId().equals(station.getId()) && strategy.getEnabled() == 1) {
            throw new ServiceException(400, "启用中的策略不能迁移场站，请先停用");
        }
        BillingMode mode = parseMode(request.getBillingMode());
        StrategyType type = parseType(request.getStrategyType());
        validateEffectiveRange(request.getEffectiveFrom(), request.getEffectiveTo());
        List<RatePeriod> periods = validateAndBuild(request.getPeriods(), type);
        if (strategy.getEnabled() == 1 && !hasBillablePrice(type, mode, periods)) {
            throw new ServiceException(400, "启用中的策略必须至少有一项有效价格");
        }

        strategy.setStationId(station.getId());
        strategy.setName(request.getName().trim());
        strategy.setBillingMode(type == StrategyType.SERVICE ? BillingMode.MIXED.name() : mode.name());
        strategy.setStrategyType(type.name());
        strategy.setEffectiveFrom(request.getEffectiveFrom());
        strategy.setEffectiveTo(request.getEffectiveTo());
        strategy.setRemark(request.getRemark());
        strategy.setUpdateBy(SecurityUtils.getUsername());
        strategyMapper.updateById(strategy);

        periodMapper.delete(new LambdaQueryWrapper<RatePeriod>().eq(RatePeriod::getStrategyId, id));
        savePeriods(id, periods);
        if (strategy.getEnabled() == 1) {
            disableOverlapping(strategy);
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        if (strategy.getEnabled() == 1) {
            throw new ServiceException(400, "启用中的策略不能删除，请先停用");
        }
        periodMapper.delete(new LambdaQueryWrapper<RatePeriod>().eq(RatePeriod::getStrategyId, id));
        strategyMapper.deleteById(id);
    }

    @Transactional
    public RateStrategyVo enable(Long id) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        Station station = requireStation(strategy.getStationId());
        if (!"ACTIVE".equals(station.getStatus())) {
            throw new ServiceException(400, "场站已停用，不能启用费率策略");
        }
        List<RatePeriod> periods = periodsFor(id);
        if (periods.isEmpty()) {
            throw new ServiceException(400, "请先配置费率时段");
        }
        validateStoredPeriods(periods);
        BillingMode mode = parseMode(strategy.getBillingMode());
        StrategyType type = typeOf(strategy);
        if (!hasBillablePrice(type, mode, periods)) {
            throw new ServiceException(400, type == StrategyType.SERVICE
                    ? "服务费策略必须至少有一个时段配置大于 0 的服务费" : "所有时段价格均为 0，不能启用");
        }
        strategyMapper.update(null, new LambdaUpdateWrapper<RateStrategy>()
                .eq(RateStrategy::getId, id)
                .set(RateStrategy::getEnabled, 1)
                .set(RateStrategy::getUpdateBy, SecurityUtils.getUsername()));
        disableOverlapping(strategy);
        return detail(id);
    }

    /** 启用即“一键切换”：同场站同类型下，生效日期重叠的其它策略自动停用。 */
    private void disableOverlapping(RateStrategy strategy) {
        List<RateStrategy> siblings = strategyMapper.selectList(new LambdaQueryWrapper<RateStrategy>()
                .eq(RateStrategy::getStationId, strategy.getStationId())
                .eq(RateStrategy::getStrategyType, typeOf(strategy).name())
                .eq(RateStrategy::getEnabled, 1)
                .ne(RateStrategy::getId, strategy.getId()));
        for (RateStrategy sibling : siblings) {
            if (rangesOverlap(strategy.getEffectiveFrom(), strategy.getEffectiveTo(),
                    sibling.getEffectiveFrom(), sibling.getEffectiveTo())) {
                strategyMapper.update(null, new LambdaUpdateWrapper<RateStrategy>()
                        .eq(RateStrategy::getId, sibling.getId())
                        .set(RateStrategy::getEnabled, 0)
                        .set(RateStrategy::getUpdateBy, SecurityUtils.getUsername()));
            }
        }
    }

    public RateStrategyVo disable(Long id) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        if (strategy.getEnabled() == 1) {
            strategyMapper.update(null, new LambdaUpdateWrapper<RateStrategy>()
                    .eq(RateStrategy::getId, id)
                    .set(RateStrategy::getEnabled, 0)
                    .set(RateStrategy::getUpdateBy, SecurityUtils.getUsername()));
        }
        return detail(id);
    }

    @Transactional
    public List<RatePeriodVo> replacePeriods(Long id, List<RatePeriodRequest> request) {
        RateStrategy strategy = requireStrategy(id);
        scopeService.assertAccess(strategy.getStationId());
        StrategyType type = typeOf(strategy);
        List<RatePeriod> periods = validateAndBuild(request, type);
        if (strategy.getEnabled() == 1
                && !hasBillablePrice(type, parseMode(strategy.getBillingMode()), periods)) {
            throw new ServiceException(400, "启用中的策略必须至少有一项有效价格");
        }
        periodMapper.delete(new LambdaQueryWrapper<RatePeriod>().eq(RatePeriod::getStrategyId, id));
        savePeriods(id, periods);
        strategy.setUpdateBy(SecurityUtils.getUsername());
        strategyMapper.updateById(strategy);
        return periodsFor(id).stream().map(this::toPeriodVo).toList();
    }

    public EnabledRate requireEnabled(Long stationId) {
        return resolve(stationId, LocalDate.now());
    }

    EnabledRate resolve(Long stationId, LocalDate today) {
        List<RateStrategy> enabled = strategyMapper.selectList(new LambdaQueryWrapper<RateStrategy>()
                .eq(RateStrategy::getStationId, stationId)
                .eq(RateStrategy::getEnabled, 1));
        List<RateStrategy> energyOptions = enabled.stream()
                .filter(strategy -> typeOf(strategy) == StrategyType.ENERGY && covers(strategy, today))
                .sorted(latestFirst())
                .toList();
        if (energyOptions.isEmpty()) {
            throw new ServiceException(400, "场站未启用费率策略，无法启动充电");
        }
        RateStrategy energy = energyOptions.get(0);
        List<RatePeriod> energyPeriods = periodsFor(energy.getId());
        if (energyPeriods.isEmpty()) {
            throw new ServiceException(400, "费率策略未配置时段，无法启动充电");
        }
        validateStoredPeriods(energyPeriods);

        RateStrategy service = enabled.stream()
                .filter(strategy -> typeOf(strategy) == StrategyType.SERVICE && covers(strategy, today))
                .sorted(latestFirst())
                .findFirst()
                .orElse(null);
        List<RatePeriod> mergedPeriods = energyPeriods;
        if (service != null) {
            List<RatePeriod> servicePeriods = periodsFor(service.getId());
            if (servicePeriods.isEmpty()) {
                throw new ServiceException(400, "服务费策略未配置时段，无法启动充电");
            }
            validateStoredPeriods(servicePeriods);
            mergedPeriods = mergeServicePeriods(energyPeriods, servicePeriods);
        }

        BillingMode mode;
        try {
            mode = parseMode(energy.getBillingMode());
        } catch (ServiceException e) {
            mode = BillingMode.MIXED;
        }
        if (!hasBillablePrice(StrategyType.ENERGY, mode, mergedPeriods)) {
            throw new ServiceException(400, "费率策略所有价格均为 0，无法启动充电");
        }
        List<RatePlan> plans = buildPlans(enabled, energy, today);
        return new EnabledRate(energy, mergedPeriods, service, plans);
    }

    /** 为订单快照生成覆盖未来生效区间的方案列表，保证跨日期订单按当日策略计价。 */
    private List<RatePlan> buildPlans(List<RateStrategy> enabled, RateStrategy current, LocalDate today) {
        Map<Long, List<RatePeriod>> periodCache = new java.util.HashMap<>();
        List<RateStrategy> services = enabled.stream()
                .filter(strategy -> typeOf(strategy) == StrategyType.SERVICE)
                .toList();
        List<RatePlan> plans = new ArrayList<>();
        for (RateStrategy energy : enabled) {
            if (typeOf(energy) != StrategyType.ENERGY) {
                continue;
            }
            boolean ended = energy.getEffectiveTo() != null && energy.getEffectiveTo().isBefore(today);
            if (ended) {
                continue;
            }
            LocalDate start = energy.getEffectiveFrom() != null && energy.getEffectiveFrom().isAfter(today)
                    ? energy.getEffectiveFrom() : today;
            LocalDate end = energy.getEffectiveTo();
            List<RatePeriod> energyPeriods = periodCache.computeIfAbsent(energy.getId(), this::periodsFor);
            if (energyPeriods.isEmpty()) {
                continue;
            }
            Set<LocalDate> boundaries = new LinkedHashSet<>();
            boundaries.add(start);
            for (RateStrategy service : services) {
                if (service.getEffectiveFrom() != null
                        && service.getEffectiveFrom().isAfter(start)
                        && (end == null || !service.getEffectiveFrom().isAfter(end))) {
                    boundaries.add(service.getEffectiveFrom());
                }
                if (service.getEffectiveTo() != null
                        && !service.getEffectiveTo().isBefore(start)
                        && (end == null || service.getEffectiveTo().isBefore(end))) {
                    boundaries.add(service.getEffectiveTo().plusDays(1));
                }
            }
            List<LocalDate> points = boundaries.stream().sorted().toList();
            for (int i = 0; i < points.size(); i++) {
                LocalDate segmentStart = points.get(i);
                LocalDate segmentEnd = i + 1 < points.size() ? points.get(i + 1).minusDays(1) : end;
                RateStrategy service = services.stream()
                        .filter(candidate -> covers(candidate, segmentStart) && overlapsRange(candidate, segmentStart, segmentEnd))
                        .sorted(latestFirst())
                        .findFirst()
                        .orElse(null);
                List<RatePeriod> periods = energyPeriods;
                if (service != null) {
                    List<RatePeriod> servicePeriods = periodCache.computeIfAbsent(
                            service.getId(), this::periodsFor);
                    if (!servicePeriods.isEmpty()) {
                        periods = mergeServicePeriods(energyPeriods, servicePeriods);
                    }
                }
                plans.add(new RatePlan(energy, service, segmentStart, segmentEnd, periods));
            }
        }
        if (plans.isEmpty()) {
            // 兜底：当前策略仍可作为唯一方案写入快照
            List<RatePeriod> periods = periodCache.computeIfAbsent(current.getId(), this::periodsFor);
            plans.add(new RatePlan(current, null, today, null, periods));
        }
        return plans;
    }

    private static boolean overlapsRange(RateStrategy strategy, LocalDate start, LocalDate end) {
        return (strategy.getEffectiveFrom() == null || end == null || !strategy.getEffectiveFrom().isAfter(end))
                && (strategy.getEffectiveTo() == null || !strategy.getEffectiveTo().isBefore(start));
    }

    /**
     * 把服务费策略按时段覆盖到电价时段上：以两套时段的全部边界切分，逐段取电价与独立服务费。
     */
    List<RatePeriod> mergeServicePeriods(List<RatePeriod> energyPeriods, List<RatePeriod> servicePeriods) {
        List<MinuteSegment> energySegments = energyPeriods.stream()
                .flatMap(period -> minuteSegments(period).stream()).toList();
        List<MinuteSegment> serviceSegments = servicePeriods.stream()
                .flatMap(period -> minuteSegments(period).stream()).toList();
        Set<Integer> boundarySet = new java.util.TreeSet<>();
        energySegments.forEach(segment -> {
            boundarySet.add(segment.start());
            boundarySet.add(segment.end());
        });
        serviceSegments.forEach(segment -> {
            boundarySet.add(segment.start());
            boundarySet.add(segment.end());
        });
        List<Integer> boundaries = new ArrayList<>(boundarySet);
        List<RatePeriod> merged = new ArrayList<>();
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            int start = boundaries.get(i);
            int end = boundaries.get(i + 1);
            MinuteSegment energy = segmentAt(energySegments, start);
            MinuteSegment service = segmentAt(serviceSegments, start);
            if (energy == null || service == null) {
                throw new ServiceException(400, "服务费策略时段未覆盖全天，无法合并计费");
            }
            RatePeriod period = new RatePeriod();
            period.setPeriodType(energy.periodType());
            period.setStartTime(toLocalTime(start));
            period.setEndTime(toLocalTime(end));
            period.setTimePrice(energy.timePrice());
            period.setEnergyPrice(energy.energyPrice());
            period.setServicePrice(service.servicePrice());
            period.setServiceUnit(service.serviceUnit());
            merged.add(period);
        }
        return merged;
    }

    private static MinuteSegment segmentAt(List<MinuteSegment> segments, int minute) {
        return segments.stream()
                .filter(segment -> minute >= segment.start() && minute < segment.end())
                .findFirst()
                .orElse(null);
    }

    private static List<MinuteSegment> minuteSegments(RatePeriod period) {
        int start = period.getStartTime().getHour() * 60 + period.getStartTime().getMinute();
        int end = period.getEndTime().getHour() * 60 + period.getEndTime().getMinute();
        if (start == end) {
            return List.of(new MinuteSegment(0, 1440, period.getPeriodType(), period.getTimePrice(),
                    period.getEnergyPrice(), period.getServicePrice(), period.getServiceUnit()));
        }
        if (end > start) {
            return List.of(new MinuteSegment(start, end, period.getPeriodType(), period.getTimePrice(),
                    period.getEnergyPrice(), period.getServicePrice(), period.getServiceUnit()));
        }
        if (end == 0) {
            return List.of(new MinuteSegment(start, 1440, period.getPeriodType(), period.getTimePrice(),
                    period.getEnergyPrice(), period.getServicePrice(), period.getServiceUnit()));
        }
        return List.of(
                new MinuteSegment(start, 1440, period.getPeriodType(), period.getTimePrice(),
                        period.getEnergyPrice(), period.getServicePrice(), period.getServiceUnit()),
                new MinuteSegment(0, end, period.getPeriodType(), period.getTimePrice(),
                        period.getEnergyPrice(), period.getServicePrice(), period.getServiceUnit()));
    }

    private static LocalTime toLocalTime(int minute) {
        if (minute >= 1440) {
            return LocalTime.MIDNIGHT;
        }
        return LocalTime.of(minute / 60, minute % 60);
    }

    private static Comparator<RateStrategy> latestFirst() {
        return Comparator.comparing(RateStrategy::getEffectiveFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(RateStrategy::getId)
                .reversed();
    }

    private static boolean covers(RateStrategy strategy, LocalDate date) {
        return (strategy.getEffectiveFrom() == null || !date.isBefore(strategy.getEffectiveFrom()))
                && (strategy.getEffectiveTo() == null || !date.isAfter(strategy.getEffectiveTo()));
    }

    private StrategyType typeOf(RateStrategy strategy) {
        return parseType(strategy.getStrategyType());
    }

    public List<RatePeriod> periodsFor(Long strategyId) {
        return periodMapper.selectList(new LambdaQueryWrapper<RatePeriod>()
                .eq(RatePeriod::getStrategyId, strategyId)
                .orderByAsc(RatePeriod::getStartTime));
    }

    private void savePeriods(Long strategyId, List<RatePeriod> periods) {
        for (RatePeriod period : periods) {
            period.setStrategyId(strategyId);
            periodMapper.insert(period);
        }
    }

    List<RatePeriod> validateAndBuild(List<RatePeriodRequest> requests) {
        return validateAndBuild(requests, StrategyType.ENERGY);
    }

    List<RatePeriod> validateAndBuild(List<RatePeriodRequest> requests, StrategyType strategyType) {
        if (requests == null || requests.isEmpty()) {
            throw new ServiceException(400, "至少配置一个费率时段");
        }
        List<RatePeriod> periods = new ArrayList<>(requests.size());
        Map<PeriodType, PeriodTemplate> templates = new EnumMap<>(PeriodType.class);
        List<Segment> segments = new ArrayList<>();
        for (RatePeriodRequest request : requests) {
            PeriodType type;
            ServiceUnit unit;
            try {
                type = PeriodType.parse(request.getPeriodType());
                unit = ServiceUnit.parse(request.getServiceUnit());
            } catch (IllegalArgumentException e) {
                throw new ServiceException(400, e.getMessage());
            }
            LocalTime start = parseTime(request.getStartTime());
            LocalTime end = parseTime(request.getEndTime());
            BigDecimal timePrice = strategyType == StrategyType.SERVICE
                    ? BigDecimal.ZERO.setScale(4) : scale(request.getTimePrice());
            BigDecimal energyPrice = strategyType == StrategyType.SERVICE
                    ? BigDecimal.ZERO.setScale(4) : scale(request.getEnergyPrice());
            BigDecimal servicePrice = scale(request.getServicePrice());

            PeriodTemplate existing = templates.get(type);
            if (existing == null) {
                templates.put(type, new PeriodTemplate(timePrice, energyPrice, servicePrice, unit));
            } else if (existing.timePrice.compareTo(timePrice) != 0
                    || existing.energyPrice.compareTo(energyPrice) != 0
                    || existing.servicePrice.compareTo(servicePrice) != 0
                    || existing.unit != unit) {
                throw new ServiceException(400, "同一时段类型的价格和服务费单位必须一致");
            }

            RatePeriod period = new RatePeriod();
            period.setPeriodType(type.name());
            period.setStartTime(start);
            period.setEndTime(end);
            period.setTimePrice(timePrice);
            period.setEnergyPrice(energyPrice);
            period.setServicePrice(servicePrice);
            period.setServiceUnit(unit.name());
            periods.add(period);
            segments.addAll(toSegments(start, end, type));
        }
        validateSegments(segments);
        return periods;
    }

    private void validateStoredPeriods(List<RatePeriod> periods) {
        List<Segment> segments = new ArrayList<>();
        Map<PeriodType, PeriodTemplate> templates = new EnumMap<>(PeriodType.class);
        for (RatePeriod period : periods) {
            PeriodType type = PeriodType.parse(period.getPeriodType());
            ServiceUnit unit = ServiceUnit.parse(period.getServiceUnit());
            PeriodTemplate existing = templates.get(type);
            RatePeriod copy = period;
            PeriodTemplate current = new PeriodTemplate(copy.getTimePrice(), copy.getEnergyPrice(),
                    copy.getServicePrice(), unit);
            if (existing == null) {
                templates.put(type, current);
            } else if (!existing.sameAs(current)) {
                throw new ServiceException(400, "同一时段类型的价格和服务费单位必须一致");
            }
            segments.addAll(toSegments(period.getStartTime(), period.getEndTime(), type));
        }
        validateSegments(segments);
    }

    private List<Segment> toSegments(LocalTime start, LocalTime end, PeriodType type) {
        int startMinute = start.getHour() * 60 + start.getMinute();
        int endMinute = end.getHour() * 60 + end.getMinute();
        if (startMinute == endMinute) {
            return List.of(new Segment(0, 1440, type));
        }
        if (endMinute > startMinute) {
            return List.of(new Segment(startMinute, endMinute, type));
        }
        if (endMinute == 0) {
            return List.of(new Segment(startMinute, 1440, type));
        }
        return List.of(new Segment(startMinute, 1440, type), new Segment(0, endMinute, type));
    }

    private void validateSegments(List<Segment> segments) {
        List<Segment> sorted = segments.stream()
                .sorted(Comparator.comparingInt(Segment::start))
                .toList();
        int cursor = 0;
        for (Segment segment : sorted) {
            if (segment.end() <= segment.start()) {
                throw new ServiceException(400, "费率时段长度不合法");
            }
            if (segment.start() < cursor) {
                throw new ServiceException(400, "费率时段存在重叠，请检查峰平谷时间");
            }
            if (segment.start() > cursor) {
                throw new ServiceException(400, "费率时段未覆盖全天，请补齐空档时段");
            }
            cursor = segment.end();
        }
        if (cursor != 1440) {
            throw new ServiceException(400, "费率时段未覆盖全天，请补齐空档时段");
        }
    }

    private boolean hasBillablePrice(StrategyType type, BillingMode mode, List<RatePeriod> periods) {
        if (type == StrategyType.SERVICE) {
            return periods.stream().anyMatch(period -> positive(period.getServicePrice()));
        }
        return periods.stream().anyMatch(period -> {
            if (positive(period.getServicePrice())) {
                return true;
            }
            if (mode.chargesTime() && positive(period.getTimePrice())) {
                return true;
            }
            return mode.chargesEnergy() && positive(period.getEnergyPrice());
        });
    }

    private StrategyType parseType(String value) {
        try {
            return StrategyType.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, e.getMessage());
        }
    }

    private void validateEffectiveRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ServiceException(400, "生效结束日期不能早于开始日期");
        }
    }

    private static boolean rangesOverlap(LocalDate from, LocalDate to, LocalDate otherFrom, LocalDate otherTo) {
        return (to == null || otherFrom == null || !to.isBefore(otherFrom))
                && (otherTo == null || from == null || !otherTo.isBefore(from));
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BillingMode parseMode(String value) {
        try {
            return BillingMode.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, e.getMessage());
        }
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(400, "时段开始和结束时间不能为空");
        }
        String text = value.trim();
        try {
            return text.length() == 5 ? LocalTime.parse(text, HH_MM) : LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new ServiceException(400, "时间格式应为 HH:mm");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private RateStrategy requireStrategy(Long id) {
        RateStrategy strategy = strategyMapper.selectById(id);
        if (strategy == null) {
            throw new ServiceException(404, "费率策略不存在");
        }
        return strategy;
    }

    private Station requireStation(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new ServiceException(404, "所属场站不存在");
        }
        return station;
    }

    private Map<Long, List<RatePeriod>> loadPeriods(List<Long> strategyIds) {
        if (strategyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return periodMapper.selectList(new LambdaQueryWrapper<RatePeriod>()
                        .in(RatePeriod::getStrategyId, strategyIds)
                        .orderByAsc(RatePeriod::getStartTime))
                .stream()
                .collect(Collectors.groupingBy(RatePeriod::getStrategyId));
    }

    private Map<Long, String> stationNames(List<RateStrategy> strategies) {
        List<Long> stationIds = strategies.stream().map(RateStrategy::getStationId).distinct().toList();
        if (stationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return stationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName));
    }

    private RateStrategyVo toVo(RateStrategy strategy, String stationName, List<RatePeriod> periods,
                                LocalDate today) {
        RateStrategyVo vo = new RateStrategyVo();
        vo.setId(strategy.getId());
        vo.setStationId(strategy.getStationId());
        vo.setStationName(stationName);
        vo.setName(strategy.getName());
        vo.setBillingMode(strategy.getBillingMode());
        vo.setStrategyType(strategy.getStrategyType() == null
                ? StrategyType.ENERGY.name() : strategy.getStrategyType());
        vo.setEffectiveFrom(strategy.getEffectiveFrom());
        vo.setEffectiveTo(strategy.getEffectiveTo());
        vo.setCurrent(strategy.getEnabled() == 1 && covers(strategy, today));
        vo.setEnabled(strategy.getEnabled());
        vo.setRemark(strategy.getRemark());
        vo.setCreateBy(strategy.getCreateBy());
        vo.setCreateTime(strategy.getCreateTime());
        vo.setUpdateBy(strategy.getUpdateBy());
        vo.setUpdateTime(strategy.getUpdateTime());
        vo.setPeriods(periods.stream().map(this::toPeriodVo).toList());
        return vo;
    }

    private RatePeriodVo toPeriodVo(RatePeriod period) {
        RatePeriodVo vo = new RatePeriodVo();
        vo.setId(period.getId());
        vo.setPeriodType(period.getPeriodType());
        vo.setStartTime(period.getStartTime().format(HH_MM));
        vo.setEndTime(period.getEndTime().format(HH_MM));
        vo.setTimePrice(period.getTimePrice());
        vo.setEnergyPrice(period.getEnergyPrice());
        vo.setServicePrice(period.getServicePrice());
        vo.setServiceUnit(period.getServiceUnit());
        return vo;
    }

    private record Segment(int start, int end, PeriodType type) {
    }

    private record MinuteSegment(int start, int end, String periodType, BigDecimal timePrice,
                                 BigDecimal energyPrice, BigDecimal servicePrice, String serviceUnit) {
    }

    private record PeriodTemplate(BigDecimal timePrice, BigDecimal energyPrice,
                                  BigDecimal servicePrice, ServiceUnit unit) {
        private boolean sameAs(PeriodTemplate other) {
            return timePrice.compareTo(other.timePrice) == 0
                    && energyPrice.compareTo(other.energyPrice) == 0
                    && servicePrice.compareTo(other.servicePrice) == 0
                    && unit == other.unit;
        }
    }
}
