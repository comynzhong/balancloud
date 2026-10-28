package com.balancloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.ExcelExportSupport;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Charger;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.ChargerMapper;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.device.service.StationScopeService;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import com.balancloud.order.domain.ChargeOrder;
import com.balancloud.order.domain.OrderRateDetail;
import com.balancloud.order.domain.OrderRefund;
import com.balancloud.order.dto.OrderQuery;
import com.balancloud.order.mapper.ChargeOrderMapper;
import com.balancloud.order.mapper.OrderRateDetailMapper;
import com.balancloud.order.mapper.OrderRefundMapper;
import com.balancloud.order.model.OrderStatus;
import com.balancloud.order.model.PaymentStatus;
import com.balancloud.order.model.StopReason;
import com.balancloud.order.vo.OrderDetailVo;
import com.balancloud.order.vo.OrderRateDetailVo;
import com.balancloud.order.vo.OrderRefundVo;
import com.balancloud.order.vo.OrderVo;
import com.balancloud.spi.dto.PayResult;
import com.balancloud.spi.dto.RefundResult;
import com.balancloud.system.service.SystemConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderQueryService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicInteger REFUND_SEQUENCE = new AtomicInteger();
    private static final int EXPORT_LIMIT = 10000;

    private final ChargeOrderMapper orderMapper;
    private final OrderRateDetailMapper detailMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final StationScopeService scopeService;
    private final BillingService billingService;
    private final AppUserMapper appUserMapper;
    private final AppVehicleMapper vehicleMapper;
    private final OrderRefundMapper refundMapper;
    private final SystemConfigService configService;
    private final PaymentGateway paymentGateway;
    private final OccupancyService occupancyService;
    private final SensitiveDataMasker masker;

    public OrderQueryService(ChargeOrderMapper orderMapper,
                             OrderRateDetailMapper detailMapper,
                             ChargerMapper chargerMapper,
                             StationMapper stationMapper,
                             StationScopeService scopeService,
                             BillingService billingService,
                             AppUserMapper appUserMapper,
                             AppVehicleMapper vehicleMapper,
                             OrderRefundMapper refundMapper,
                             SystemConfigService configService,
                             PaymentGateway paymentGateway,
                             OccupancyService occupancyService,
                             SensitiveDataMasker masker) {
        this.orderMapper = orderMapper;
        this.detailMapper = detailMapper;
        this.chargerMapper = chargerMapper;
        this.stationMapper = stationMapper;
        this.scopeService = scopeService;
        this.billingService = billingService;
        this.appUserMapper = appUserMapper;
        this.vehicleMapper = vehicleMapper;
        this.refundMapper = refundMapper;
        this.configService = configService;
        this.paymentGateway = paymentGateway;
        this.occupancyService = occupancyService;
        this.masker = masker;
    }

    public TableDataInfo list(OrderQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        List<Long> scope = authorizedScope(query);
        if (scope != null && scope.isEmpty()) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        Page<ChargeOrder> page = new Page<>(pageNum, pageSize);
        Page<ChargeOrder> result = orderMapper.selectPage(page, buildWrapper(query, scope));
        if (result.getRecords().isEmpty()) {
            return TableDataInfo.of(result.getTotal(), Collections.emptyList());
        }
        List<OrderVo> rows = toVos(result.getRecords());
        maskForDisplay(rows);
        return TableDataInfo.of(result.getTotal(), rows);
    }

    public OrderDetailVo detail(Long id) {
        ChargeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        scopeService.assertAccess(order.getStationId());
        OrderDetailVo vo = new OrderDetailVo();
        fill(vo, order, charger(order.getChargerId()), station(order.getStationId()),
                appUser(order.getAppUserId()), vehicle(order.getVehicleId()));
        OrderRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, id)
                .last("LIMIT 1"));
        if (refund != null) {
            applyRefund(vo, refund);
            vo.setRefund(toRefundVo(refund));
        }
        List<OrderRateDetail> details = detailMapper.selectList(new LambdaQueryWrapper<OrderRateDetail>()
                .eq(OrderRateDetail::getOrderId, id));
        List<OrderRateDetailVo> detailVos = details.stream()
                .sorted(Comparator.comparingInt(item -> periodOrder(item.getPeriodType())))
                .map(item -> {
                    OrderRateDetailVo rateVo = new OrderRateDetailVo();
                    BeanUtils.copyProperties(item, rateVo);
                    return rateVo;
                })
                .toList();
        vo.setDetails(detailVos);
        vo.setRateSnapshot(billingService.parseSnapshot(order.getRateSnapshotJson()));
        if (masker.displayEnabled()) {
            maskOrder(vo, true);
        }
        return vo;
    }

    public OrderDetailVo pay(Long id) {
        ChargeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        scopeService.assertAccess(order.getStationId());
        if (PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            throw new ServiceException(400, "订单已退款，不能再次支付");
        }
        if (PaymentStatus.PAID.name().equals(order.getPaymentStatus())) {
            return detail(id);
        }
        if (occupancyService.isPortOccupiedFor(order)) {
            throw new ServiceException(400, "订单仍处于占用状态，请先拔枪结算占位费");
        }
        OrderStatus status = OrderStatus.parse(order.getStatus());
        if (status == OrderStatus.FAILED) {
            throw new ServiceException(400, "启动失败的订单无需支付");
        }
        if (status.isActive()) {
            throw new ServiceException(400, "订单尚未结束，不能支付");
        }
        if (paymentGateway.realChannel()) {
            throw new ServiceException(400, "真实支付通道下不支持后台代为支付，请让用户在客户端完成支付");
        }
        PayResult result = paymentGateway.pay(order, SecurityUtils.getUsername());
        if (!result.success()) {
            throw new ServiceException(502, result.message());
        }
        orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, id)
                .eq(ChargeOrder::getPaymentStatus, PaymentStatus.UNPAID.name())
                .set(ChargeOrder::getPaymentStatus, PaymentStatus.PAID.name())
                .set(ChargeOrder::getPaidAt, LocalDateTime.now())
                .set(ChargeOrder::getUpdateBy, SecurityUtils.getUsername()));
        return detail(id);
    }

    @Transactional
    public OrderDetailVo refund(Long id, String reason) {
        if (!configService.isRefundEnabled()) {
            throw new ServiceException(403, "退款功能已关闭，请联系平台管理员");
        }
        ChargeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        scopeService.assertAccess(order.getStationId());
        OrderStatus status = OrderStatus.parse(order.getStatus());
        if (status.isActive()) {
            throw new ServiceException(400, "订单进行中，不能退款");
        }
        if (status == OrderStatus.FAILED) {
            throw new ServiceException(400, "启动失败的订单无需退款");
        }
        if (PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            throw new ServiceException(409, "该订单已退款，请勿重复操作");
        }
        if (!PaymentStatus.PAID.name().equals(order.getPaymentStatus())) {
            throw new ServiceException(400, "订单未支付，无法退款");
        }
        if (order.getTotalFee() == null || order.getTotalFee().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "订单金额为 0，无需退款");
        }
        OrderRefund existingRefund = refundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, id)
                .last("LIMIT 1"));
        if (existingRefund != null) {
            if ("SUCCESS".equals(existingRefund.getStatus())) {
                throw new ServiceException(409, "该订单已退款，请勿重复操作");
            }
            if ("PROCESSING".equals(existingRefund.getStatus())) {
                throw new ServiceException(409, "退款正在处理中，请等待通道结果");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String refundNo = nextRefundNo(now);
        String reasonText = StringUtils.hasText(reason) ? reason.trim() : "运营后台模拟退款";
        RefundResult refundResult = paymentGateway.refund(order, refundNo, reasonText,
                SecurityUtils.getUsername());
        if (refundResult.pending()) {
            // 通道已受理但钱还没退：订单保持 PAID，只把退款单置 PROCESSING，等退款回调落终态
            saveRefund(existingRefund, refundNo, order, reasonText, "PROCESSING", now);
            return detail(id);
        }
        if (!refundResult.success()) {
            throw new ServiceException(502, refundResult.message());
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ChargeOrder>()
                .eq(ChargeOrder::getId, id)
                .eq(ChargeOrder::getPaymentStatus, PaymentStatus.PAID.name())
                .set(ChargeOrder::getPaymentStatus, PaymentStatus.REFUNDED.name())
                .set(ChargeOrder::getUpdateBy, SecurityUtils.getUsername()));
        if (updated == 0) {
            throw new ServiceException(409, "订单支付状态已变化，请刷新后重试");
        }
        saveRefund(existingRefund, refundNo, order, reasonText, "SUCCESS", now);
        return detail(id);
    }

    private void saveRefund(OrderRefund existing, String refundNo, ChargeOrder order,
                            String reason, String status, LocalDateTime now) {
        if (existing == null) {
            OrderRefund refund = new OrderRefund();
            refund.setRefundNo(refundNo);
            refund.setOrderId(order.getId());
            refund.setOrderNo(order.getOrderNo());
            refund.setStationId(order.getStationId());
            refund.setAppUserId(order.getAppUserId());
            refund.setAmount(order.getTotalFee().setScale(4, RoundingMode.HALF_UP));
            refund.setReason(truncate(reason, 200));
            refund.setStatus(status);
            refund.setOperator(SecurityUtils.getUsername());
            refund.setRefundTime(now);
            refund.setCreateTime(now);
            refundMapper.insert(refund);
            return;
        }
        existing.setRefundNo(refundNo);
        existing.setAmount(order.getTotalFee().setScale(4, RoundingMode.HALF_UP));
        existing.setReason(truncate(reason, 200));
        existing.setStatus(status);
        existing.setOperator(SecurityUtils.getUsername());
        existing.setRefundTime(now);
        refundMapper.updateById(existing);
    }

    public List<OrderVo> listByUser(Long userId, List<Long> stationScope, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        if (stationScope != null && stationScope.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ChargeOrder> wrapper = new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getAppUserId, userId);
        if (stationScope != null) {
            wrapper.in(ChargeOrder::getStationId, stationScope);
        }
        wrapper.orderByDesc(ChargeOrder::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return toVos(orderMapper.selectList(wrapper));
    }

    public void export(OrderQuery query, HttpServletResponse response) {
        List<Long> scope = authorizedScope(query);
        List<ChargeOrder> orders = Collections.emptyList();
        if (scope == null || !scope.isEmpty()) {
            orders = orderMapper.selectList(buildWrapper(query, scope).last("LIMIT " + EXPORT_LIMIT));
        }
        List<OrderVo> rows = toVos(orders);
        maskForExport(rows);
        String filename = "orders_" + LocalDateTime.now().format(FILE_TIME) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + filename + "; filename*=UTF-8''" + filename);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("充电订单");
            String[] headers = {"订单号", "场站", "设备编号", "端口", "用户", "车牌号", "计费模式", "开始时间", "结束时间",
                    "时长(分钟)", "电量(kWh)", "时间费(元)", "电量费(元)", "服务费(元)", "总金额(元)",
                    "订单状态", "支付状态", "退款单号", "退款金额(元)", "停止原因", "创建人", "支付时间"};
            int[] widths = {22, 18, 16, 8, 20, 12, 10, 20, 20, 12, 14, 13, 13, 13, 13, 12, 10, 22, 14, 14, 12, 20};
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            int rowIndex = 1;
            for (OrderVo vo : rows) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(text(vo.getOrderNo()));
                row.createCell(col++).setCellValue(text(vo.getStationName()));
                row.createCell(col++).setCellValue(text(vo.getDeviceNo()));
                row.createCell(col++).setCellValue(vo.getPortNo() == null ? "" : String.valueOf(vo.getPortNo()));
                row.createCell(col++).setCellValue(userLabel(vo));
                row.createCell(col++).setCellValue(text(vo.getVehiclePlateNo()));
                row.createCell(col++).setCellValue(billingModeLabel(vo.getBillingMode()));
                row.createCell(col++).setCellValue(time(vo.getStartTime()));
                row.createCell(col++).setCellValue(time(vo.getEndTime()));
                row.createCell(col++).setCellValue(vo.getDurationSeconds() == null ? 0 : vo.getDurationSeconds() / 60);
                money(row.createCell(col++), vo.getEnergyKwh(), moneyStyle);
                money(row.createCell(col++), vo.getTimeFee(), moneyStyle);
                money(row.createCell(col++), vo.getEnergyFee(), moneyStyle);
                money(row.createCell(col++), vo.getServiceFee(), moneyStyle);
                money(row.createCell(col++), vo.getTotalFee(), moneyStyle);
                row.createCell(col++).setCellValue(orderStatusLabel(vo.getStatus()));
                row.createCell(col++).setCellValue(paymentStatusLabel(vo.getPaymentStatus()));
                row.createCell(col++).setCellValue(text(vo.getRefundNo()));
                money(row.createCell(col++), vo.getRefundAmount(), moneyStyle);
                row.createCell(col++).setCellValue(stopReasonLabel(vo.getStopReason()));
                row.createCell(col++).setCellValue(text(vo.getCreateBy()));
                row.createCell(col).setCellValue(time(vo.getPaidAt()));
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(rowIndex - 1, 1), 0, headers.length - 1));
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new ServiceException(500, "导出订单失败：" + e.getMessage());
        }
    }

    public void exportRefunds(OrderQuery query, HttpServletResponse response) {
        List<Long> scope = authorizedScope(query);
        List<OrderVo> rows = Collections.emptyList();
        Map<Long, OrderRefund> refunds = Collections.emptyMap();
        if (scope == null || !scope.isEmpty()) {
            List<ChargeOrder> orders = orderMapper.selectList(buildWrapper(query, scope)
                    .eq(ChargeOrder::getPaymentStatus, PaymentStatus.REFUNDED.name())
                    .last("LIMIT " + EXPORT_LIMIT));
            refunds = loadRefunds(orders);
            rows = toVos(orders).stream()
                    .filter(vo -> vo.getRefundNo() != null)
                    .sorted(Comparator.comparing(OrderVo::getRefundTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            maskForExport(rows);
        }
        XSSFWorkbook workbook = ExcelExportSupport.workbook();
        String[] headers = {"退款单号", "订单号", "场站", "设备编号", "端口", "用户", "车牌号",
                "退款金额(元)", "原订单金额(元)", "退款原因", "退款时间", "操作人"};
        int[] widths = {22, 22, 18, 16, 8, 20, 12, 14, 14, 26, 20, 12};
        Sheet sheet = ExcelExportSupport.sheet(workbook, "退款记录", headers, widths);
        CellStyle moneyStyle = ExcelExportSupport.moneyStyle(workbook);
        int index = 1;
        for (OrderVo vo : rows) {
            Row row = sheet.createRow(index++);
            int col = 0;
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getRefundNo()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getOrderNo()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getStationName()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getDeviceNo()));
            row.createCell(col++).setCellValue(vo.getPortNo() == null ? "" : String.valueOf(vo.getPortNo()));
            row.createCell(col++).setCellValue(userLabel(vo));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getVehiclePlateNo()));
            ExcelExportSupport.money(row, col++, vo.getRefundAmount(), moneyStyle);
            ExcelExportSupport.money(row, col++, vo.getTotalFee(), moneyStyle);
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getRefundReason()));
            row.createCell(col++).setCellValue(ExcelExportSupport.time(vo.getRefundTime()));
            OrderRefund refund = refunds.get(vo.getId());
            row.createCell(col).setCellValue(refund == null ? "" : ExcelExportSupport.text(refund.getOperator()));
        }
        ExcelExportSupport.finish(workbook, sheet, index - 1, headers.length, response, "refunds");
    }

    private LambdaQueryWrapper<ChargeOrder> buildWrapper(OrderQuery query, List<Long> scope) {
        if (query.getStationId() != null) {
            scopeService.assertAccess(query.getStationId());
        }
        LambdaQueryWrapper<ChargeOrder> wrapper = new LambdaQueryWrapper<>();
        if (scope != null) {
            wrapper.in(ChargeOrder::getStationId, scope);
        }
        if (query.getStationId() != null) {
            wrapper.eq(ChargeOrder::getStationId, query.getStationId());
        }
        if (query.getChargerId() != null) {
            wrapper.eq(ChargeOrder::getChargerId, query.getChargerId());
        }
        if (query.getPortNo() != null) {
            wrapper.eq(ChargeOrder::getPortNo, query.getPortNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ChargeOrder::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getPaymentStatus())) {
            wrapper.eq(ChargeOrder::getPaymentStatus, query.getPaymentStatus().trim());
        }
        if (query.getBeginTime() != null) {
            wrapper.ge(ChargeOrder::getStartTime, query.getBeginTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(ChargeOrder::getStartTime, query.getEndTime());
        }
        if (query.getRefundBeginTime() != null || query.getRefundEndTime() != null) {
            LambdaQueryWrapper<OrderRefund> refundWrapper = new LambdaQueryWrapper<>();
            if (query.getRefundBeginTime() != null) {
                refundWrapper.ge(OrderRefund::getRefundTime, query.getRefundBeginTime());
            }
            if (query.getRefundEndTime() != null) {
                refundWrapper.le(OrderRefund::getRefundTime, query.getRefundEndTime());
            }
            List<Long> refundedOrderIds = refundMapper.selectList(refundWrapper).stream()
                    .map(OrderRefund::getOrderId).toList();
            if (refundedOrderIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(ChargeOrder::getId, refundedOrderIds);
            }
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            List<Long> chargerIds = chargerMapper.selectList(new LambdaQueryWrapper<Charger>()
                            .and(w -> w.like(Charger::getDeviceNo, keyword).or().like(Charger::getName, keyword)))
                    .stream().map(Charger::getId).toList();
            List<Long> userIds = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                            .and(w -> w.like(AppUser::getPhone, keyword)
                                    .or().like(AppUser::getNickname, keyword)))
                    .stream().map(AppUser::getId).toList();
            List<Long> vehicleIds = vehicleMapper.selectList(new LambdaQueryWrapper<AppVehicle>()
                            .like(AppVehicle::getPlateNo, keyword))
                    .stream().map(AppVehicle::getId).toList();
            wrapper.and(w -> {
                w.like(ChargeOrder::getOrderNo, keyword);
                if (!chargerIds.isEmpty()) {
                    w.or().in(ChargeOrder::getChargerId, chargerIds);
                }
                if (!userIds.isEmpty()) {
                    w.or().in(ChargeOrder::getAppUserId, userIds);
                }
                if (!vehicleIds.isEmpty()) {
                    w.or().in(ChargeOrder::getVehicleId, vehicleIds);
                }
            });
        }
        wrapper.orderByDesc(ChargeOrder::getId);
        return wrapper;
    }

    private List<Long> authorizedScope(OrderQuery query) {
        List<Long> scope = scopeService.authorizedStationIds();
        if (query.getStationId() != null) {
            scopeService.assertAccess(query.getStationId());
        }
        return scope;
    }

    private void maskForDisplay(List<? extends OrderVo> rows) {
        if (!masker.displayEnabled()) {
            return;
        }
        rows.forEach(vo -> maskOrder(vo, true));
    }

    private void maskForExport(List<? extends OrderVo> rows) {
        if (!masker.exportEnabled()) {
            return;
        }
        rows.forEach(vo -> maskOrder(vo, false));
    }

    private void maskOrder(OrderVo vo, boolean display) {
        vo.setAppUserPhone(display ? masker.displayPhone(vo.getAppUserPhone())
                : masker.exportPhone(vo.getAppUserPhone()));
        vo.setVehiclePlateNo(display ? masker.displayPlate(vo.getVehiclePlateNo())
                : masker.exportPlate(vo.getVehiclePlateNo()));
    }

    private List<OrderVo> toVos(List<ChargeOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Charger> chargers = chargerMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getChargerId).distinct().toList())
                .stream().collect(Collectors.toMap(Charger::getId, item -> item));
        Map<Long, Station> stations = stationMapper.selectBatchIds(
                        orders.stream().map(ChargeOrder::getStationId).distinct().toList())
                .stream().collect(Collectors.toMap(Station::getId, item -> item));
        Map<Long, AppUser> appUsers = loadAppUsers(orders);
        Map<Long, AppVehicle> vehicles = loadVehicles(orders);
        Map<Long, OrderRefund> refunds = loadRefunds(orders);
        return orders.stream().map(order -> {
            OrderVo vo = new OrderVo();
            fill(vo, order, chargers.get(order.getChargerId()), stations.get(order.getStationId()),
                    appUsers.get(order.getAppUserId()), vehicles.get(order.getVehicleId()));
            OrderRefund refund = refunds.get(order.getId());
            if (refund != null) {
                applyRefund(vo, refund);
            }
            return vo;
        }).toList();
    }

    private Map<Long, AppVehicle> loadVehicles(List<ChargeOrder> orders) {
        List<Long> vehicleIds = orders.stream().map(ChargeOrder::getVehicleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (vehicleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return vehicleMapper.selectBatchIds(vehicleIds).stream()
                .collect(Collectors.toMap(AppVehicle::getId, Function.identity()));
    }

    private Map<Long, OrderRefund> loadRefunds(List<ChargeOrder> orders) {
        List<Long> orderIds = orders.stream().map(ChargeOrder::getId)
                .filter(java.util.Objects::nonNull).toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return refundMapper.selectList(new LambdaQueryWrapper<OrderRefund>()
                        .in(OrderRefund::getOrderId, orderIds))
                .stream().collect(Collectors.toMap(OrderRefund::getOrderId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, AppUser> loadAppUsers(List<ChargeOrder> orders) {
        List<Long> userIds = orders.stream().map(ChargeOrder::getAppUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private void fill(OrderVo vo, ChargeOrder order, Charger charger, Station station,
                      AppUser appUser, AppVehicle vehicle) {
        BeanUtils.copyProperties(order, vo);
        if (charger != null) {
            vo.setDeviceNo(charger.getDeviceNo());
            vo.setDeviceName(charger.getName());
        }
        if (station != null) {
            vo.setStationName(station.getName());
        }
        if (appUser != null) {
            vo.setAppUserNickname(appUser.getNickname());
            vo.setAppUserPhone(appUser.getPhone());
        }
        if (vehicle != null) {
            vo.setVehiclePlateNo(vehicle.getPlateNo());
        }
    }

    private void applyRefund(OrderVo vo, OrderRefund refund) {
        vo.setRefundNo(refund.getRefundNo());
        vo.setRefundAmount(refund.getAmount());
        vo.setRefundReason(refund.getReason());
        vo.setRefundTime(refund.getRefundTime());
    }

    private OrderRefundVo toRefundVo(OrderRefund refund) {
        OrderRefundVo vo = new OrderRefundVo();
        BeanUtils.copyProperties(refund, vo);
        return vo;
    }

    private AppVehicle vehicle(Long id) {
        return id == null ? null : vehicleMapper.selectById(id);
    }

    private Charger charger(Long id) {
        return id == null ? null : chargerMapper.selectById(id);
    }

    private Station station(Long id) {
        return id == null ? null : stationMapper.selectById(id);
    }

    private AppUser appUser(Long id) {
        return id == null ? null : appUserMapper.selectById(id);
    }

    private String nextRefundNo(LocalDateTime now) {
        int seq = Math.floorMod(REFUND_SEQUENCE.incrementAndGet(), 10000);
        return "RF" + now.format(FILE_TIME) + String.format("%04d", seq);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String userLabel(OrderVo vo) {
        if (vo.getAppUserId() == null) {
            return "后台发起";
        }
        String nickname = text(vo.getAppUserNickname());
        String phone = text(vo.getAppUserPhone());
        if (phone.isEmpty()) {
            return nickname;
        }
        return nickname.isEmpty() ? phone : nickname + " " + phone;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void money(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(style);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private int periodOrder(String periodType) {
        return switch (periodType) {
            case "PEAK" -> 0;
            case "FLAT" -> 1;
            case "VALLEY" -> 2;
            default -> 3;
        };
    }

    private String billingModeLabel(String mode) {
        return switch (mode == null ? "" : mode) {
            case "TIME" -> "按时长";
            case "ENERGY" -> "按电量";
            case "MIXED" -> "混合计费";
            default -> text(mode);
        };
    }

    private String orderStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING_START" -> "启动中";
            case "CHARGING" -> "充电中";
            case "STOPPING" -> "停止中";
            case "COMPLETED" -> "已完成";
            case "FAILED" -> "启动失败";
            case "ABNORMAL" -> "异常结束";
            default -> text(status);
        };
    }

    private String paymentStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PAID" -> "已支付";
            case "REFUNDED" -> "已退款";
            default -> "未支付";
        };
    }

    private String stopReasonLabel(String reason) {
        if (reason == null) {
            return "";
        }
        return switch (reason) {
            case "USER_STOP" -> "用户停止";
            case "DEVICE_STOP" -> "设备停止";
            case "FULL" -> "充满自停";
            case "TIME_LIMIT" -> "达到时长上限";
            case "AMOUNT_LIMIT" -> "达到金额上限";
            case "NO_LOAD" -> "空载自停";
            case "FAULT" -> "设备故障";
            case "DEVICE_OFFLINE" -> "设备离线";
            case "STOP_TIMEOUT" -> "停止超时";
            case "START_FAILED" -> "启动失败";
            default -> reason;
        };
    }
}
