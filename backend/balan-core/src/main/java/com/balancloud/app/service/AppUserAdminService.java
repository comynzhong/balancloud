package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.dto.AppUserQuery;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.app.security.AppTokenService;
import com.balancloud.app.vo.AppUserAdminVo;
import com.balancloud.app.vo.AppUserDetailVo;
import com.balancloud.app.vo.VehicleVo;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.ExcelExportSupport;
import com.balancloud.device.domain.Station;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.service.OrderQueryService;
import com.balancloud.order.vo.OrderVo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppUserAdminService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_START.name(), OrderStatus.CHARGING.name(), OrderStatus.STOPPING.name());

    private final AppUserMapper userMapper;
    private final AppVehicleMapper vehicleMapper;
    private final ChargeOrderMapper orderMapper;
    private final StationMapper stationMapper;
    private final AppUserVisibilityService visibilityService;
    private final AppTokenService tokenService;
    private final OrderQueryService orderQueryService;
    private final SensitiveDataMasker masker;

    public AppUserAdminService(AppUserMapper userMapper,
                               AppVehicleMapper vehicleMapper,
                               ChargeOrderMapper orderMapper,
                               StationMapper stationMapper,
                               AppUserVisibilityService visibilityService,
                               AppTokenService tokenService,
                               OrderQueryService orderQueryService,
                               SensitiveDataMasker masker) {
        this.userMapper = userMapper;
        this.vehicleMapper = vehicleMapper;
        this.orderMapper = orderMapper;
        this.stationMapper = stationMapper;
        this.visibilityService = visibilityService;
        this.tokenService = tokenService;
        this.orderQueryService = orderQueryService;
        this.masker = masker;
    }

    public TableDataInfo list(AppUserQuery query) {
        LambdaQueryWrapper<AppUser> wrapper = buildWrapper(query);
        if (wrapper == null) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        Page<AppUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AppUserAdminVo> rows = toVos(page.getRecords());
        if (masker.displayEnabled()) {
            rows.forEach(row -> row.setPhone(masker.displayPhone(row.getPhone())));
        }
        return TableDataInfo.of(page.getTotal(), rows);
    }

    public void export(AppUserQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<AppUser> wrapper = buildWrapper(query);
        List<AppUserAdminVo> rows = wrapper == null
                ? Collections.emptyList()
                : toVos(userMapper.selectList(wrapper.last("LIMIT 10000")));
        if (masker.exportEnabled()) {
            rows.forEach(row -> row.setPhone(masker.exportPhone(row.getPhone())));
        }
        XSSFWorkbook workbook = ExcelExportSupport.workbook();
        String[] headers = {"用户ID", "昵称", "手机号", "账号状态", "订单数", "已支付金额(元)", "已退款金额(元)",
                "车辆数", "最近登录", "注册时间", "最近订单", "当前充电订单", "当前场站", "当前端口"};
        int[] widths = {8, 18, 16, 10, 10, 16, 16, 8, 20, 20, 20, 22, 18, 10};
        Sheet sheet = ExcelExportSupport.sheet(workbook, "小程序用户", headers, widths);
        CellStyle moneyStyle = ExcelExportSupport.moneyStyle(workbook);
        int index = 1;
        for (AppUserAdminVo vo : rows) {
            Row row = sheet.createRow(index++);
            int col = 0;
            row.createCell(col++).setCellValue(vo.getId() == null ? 0 : vo.getId());
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getNickname()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getPhone()));
            row.createCell(col++).setCellValue("0".equals(vo.getStatus()) ? "正常" : "停用");
            row.createCell(col++).setCellValue(vo.getOrderCount());
            ExcelExportSupport.money(row, col++, vo.getPaidAmount(), moneyStyle);
            ExcelExportSupport.money(row, col++, vo.getRefundedAmount(), moneyStyle);
            row.createCell(col++).setCellValue(vo.getVehicleCount());
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getLastLoginAt()));
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getCreateTime()));
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getLastOrderTime()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getActiveOrderNo()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getActiveStationName()));
            row.createCell(col).setCellValue(vo.getActivePortNo() == null ? "" : String.valueOf(vo.getActivePortNo()));
        }
        ExcelExportSupport.finish(workbook, sheet, index - 1, headers.length, response, "app_users");
    }

    private LambdaQueryWrapper<AppUser> buildWrapper(AppUserQuery query) {
        List<Long> visibleUserIds = visibilityService.visibleUserIds();
        if (visibleUserIds != null && visibleUserIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        if (visibleUserIds != null) {
            wrapper.in(AppUser::getId, visibleUserIds);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AppUser::getNickname, keyword)
                    .or().like(AppUser::getPhone, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AppUser::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(AppUser::getId);
        return wrapper;
    }

    public AppUserDetailVo detail(Long id) {
        AppUser user = requireUser(id);
        visibilityService.assertVisible(id);
        AppUserDetailVo vo = new AppUserDetailVo();
        fillBase(vo, user);
        Map<Long, Stats> stats = stats(List.of(id));
        fillStats(vo, stats.get(id));
        Map<Long, ChargeOrder> active = activeOrders(List.of(id));
        fillActive(vo, active.get(id));
        vo.setVehicleCount(vehicleMapper.selectCount(new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getAppUserId, id)).intValue());
        List<VehicleVo> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<AppVehicle>()
                        .eq(AppVehicle::getAppUserId, id)
                        .orderByDesc(AppVehicle::getIsDefault)
                        .orderByDesc(AppVehicle::getId))
                .stream().map(VehicleVo::of).toList();
        List<Long> scope = visibilityService.visibleUserIds();
        List<OrderVo> recentOrders = orderQueryService.listByUser(id, scope, 10);
        if (masker.displayEnabled()) {
            vo.setPhone(masker.displayPhone(vo.getPhone()));
            vehicles.forEach(item -> item.setPlateNo(masker.displayPlate(item.getPlateNo())));
            recentOrders.forEach(item -> {
                item.setAppUserPhone(masker.displayPhone(item.getAppUserPhone()));
                item.setVehiclePlateNo(masker.displayPlate(item.getVehiclePlateNo()));
            });
        }
        vo.setVehicles(vehicles);
        vo.setRecentOrders(recentOrders);
        return vo;
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException(400, "用户状态不合法");
        }
        AppUser user = requireUser(id);
        visibilityService.assertVisible(id);
        if (!Objects.equals(user.getStatus(), status)) {
            user.setStatus(status);
            userMapper.updateById(user);
            // 停用后已有 App token 必须立即失效；重新启用后可正常登录
            tokenService.evictUser(id);
        }
    }

    private List<AppUserAdminVo> toVos(List<AppUser> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = users.stream().map(AppUser::getId).toList();
        Map<Long, Stats> stats = stats(ids);
        Map<Long, ChargeOrder> active = activeOrders(ids);
        Map<Long, Long> vehicleCounts = vehicleCounts(ids);
        return users.stream().map(user -> {
            AppUserAdminVo vo = new AppUserAdminVo();
            fillBase(vo, user);
            fillStats(vo, stats.get(user.getId()));
            fillActive(vo, active.get(user.getId()));
            vo.setVehicleCount(vehicleCounts.getOrDefault(user.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    private void fillBase(AppUserAdminVo vo, AppUser user) {
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());
    }

    private void fillStats(AppUserAdminVo vo, Stats stats) {
        vo.setOrderCount(stats == null ? 0 : stats.orderCount());
        vo.setPaidAmount(stats == null ? BigDecimal.ZERO : stats.paidAmount());
        vo.setRefundedAmount(stats == null ? BigDecimal.ZERO : stats.refundedAmount());
        vo.setLastOrderTime(stats == null ? null : stats.lastOrderTime());
    }

    private void fillActive(AppUserAdminVo vo, ChargeOrder order) {
        if (order == null) {
            return;
        }
        vo.setActiveOrderId(order.getId());
        vo.setActiveOrderNo(order.getOrderNo());
        vo.setActivePortNo(order.getPortNo());
        vo.setActiveStartTime(order.getStartTime());
        vo.setActiveDurationSeconds(order.getDurationSeconds());
        Station station = order.getStationId() == null ? null : stationMapper.selectById(order.getStationId());
        if (station != null) {
            vo.setActiveStationName(station.getName());
        }
    }

    private Map<Long, Stats> stats(List<Long> userIds) {
        List<Long> scope = visibilityService.visibleUserIds();
        if (scope != null && scope.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<ChargeOrder> wrapper = new QueryWrapper<ChargeOrder>()
                .select("app_user_id AS user_id",
                        "COUNT(*) AS order_count",
                        "IFNULL(SUM(CASE WHEN payment_status = 'PAID' THEN total_fee ELSE 0 END), 0) AS paid_amount",
                        "IFNULL(SUM(CASE WHEN payment_status = 'REFUNDED' THEN total_fee ELSE 0 END), 0) AS refunded_amount",
                        "MAX(create_time) AS last_order_time")
                .in("app_user_id", userIds)
                .groupBy("app_user_id");
        if (scope != null) {
            wrapper.in("station_id", scope);
        }
        Map<Long, Stats> result = new java.util.HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
            Long userId = asLong(row, "user_id");
            if (userId != null) {
                result.put(userId, new Stats(
                        asLong(row, "order_count") == null ? 0 : asLong(row, "order_count"),
                        asDecimal(row, "paid_amount"),
                        asDecimal(row, "refunded_amount"),
                        asTime(row, "last_order_time")));
            }
        }
        return result;
    }

    private Map<Long, ChargeOrder> activeOrders(List<Long> userIds) {
        List<Long> scope = visibilityService.visibleUserIds();
        if (scope != null && scope.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<ChargeOrder> wrapper = new LambdaQueryWrapper<ChargeOrder>()
                .in(ChargeOrder::getAppUserId, userIds)
                .in(ChargeOrder::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChargeOrder::getId);
        if (scope != null) {
            wrapper.in(ChargeOrder::getStationId, scope);
        }
        List<ChargeOrder> orders = orderMapper.selectList(wrapper);
        return orders.stream().collect(Collectors.toMap(ChargeOrder::getAppUserId,
                Function.identity(), (first, second) -> first));
    }

    private Map<Long, Long> vehicleCounts(List<Long> userIds) {
        List<Map<String, Object>> rows = vehicleMapper.selectMaps(new QueryWrapper<AppVehicle>()
                .select("app_user_id AS user_id", "COUNT(*) AS vehicle_count")
                .in("app_user_id", userIds)
                .groupBy("app_user_id"));
        Map<Long, Long> result = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = asLong(row, "user_id");
            Long count = asLong(row, "vehicle_count");
            if (userId != null) {
                result.put(userId, count == null ? 0L : count);
            }
        }
        return result;
    }

    private AppUser requireUser(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException(404, "小程序用户不存在");
        }
        return user;
    }

    private Object value(Map<String, Object> row, String column) {
        Object direct = row.get(column);
        if (direct != null) {
            return direct;
        }
        String normalized = column.replace("_", "").toLowerCase();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().replace("_", "").toLowerCase().equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long asLong(Map<String, Object> row, String column) {
        Object value = value(row, column);
        return value instanceof Number number ? number.longValue()
                : (value == null ? null : Long.parseLong(String.valueOf(value)));
    }

    private BigDecimal asDecimal(Map<String, Object> row, String column) {
        Object value = value(row, column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private java.time.LocalDateTime asTime(Map<String, Object> row, String column) {
        Object value = value(row, column);
        if (value instanceof java.time.LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private record Stats(long orderCount, BigDecimal paidAmount, BigDecimal refundedAmount,
                         java.time.LocalDateTime lastOrderTime) {
    }
}
