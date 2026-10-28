package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.dto.VehicleQuery;
import com.balancloud.app.dto.VehicleRequest;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.app.vo.VehicleVo;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.ExcelExportSupport;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AppVehicleService {

    /** 不含 I / O / Q，避免与 1 / 0 混淆。 */
    private static final Pattern VIN_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    private final AppVehicleMapper vehicleMapper;
    private final AppUserMapper userMapper;
    private final AppUserVisibilityService visibilityService;
    private final SensitiveDataMasker masker;

    public AppVehicleService(AppVehicleMapper vehicleMapper,
                             AppUserMapper userMapper,
                             AppUserVisibilityService visibilityService,
                             SensitiveDataMasker masker) {
        this.vehicleMapper = vehicleMapper;
        this.userMapper = userMapper;
        this.visibilityService = visibilityService;
        this.masker = masker;
    }

    public TableDataInfo adminList(VehicleQuery query) {
        LambdaQueryWrapper<AppVehicle> wrapper = buildAdminWrapper(query);
        if (wrapper == null) {
            return TableDataInfo.of(0, Collections.emptyList());
        }
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        Page<AppVehicle> page = vehicleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<VehicleVo> rows = toVos(page.getRecords());
        if (masker.displayEnabled()) {
            rows.forEach(row -> {
                row.setPlateNo(masker.displayPlate(row.getPlateNo()));
                row.setAppUserPhone(masker.displayPhone(row.getAppUserPhone()));
            });
        }
        return TableDataInfo.of(page.getTotal(), rows);
    }

    /**
     * 编辑回填使用原文；列表仍按脱敏开关展示。
     */
    public VehicleVo adminDetail(Long id) {
        AppVehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new ServiceException(404, "车辆不存在");
        }
        visibilityService.assertVisible(vehicle.getAppUserId());
        return toVos(List.of(vehicle)).get(0);
    }

    public void exportAdmin(VehicleQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<AppVehicle> wrapper = buildAdminWrapper(query);
        List<VehicleVo> rows = wrapper == null
                ? Collections.emptyList()
                : toVos(vehicleMapper.selectList(wrapper.last("LIMIT 10000")));
        if (masker.exportEnabled()) {
            rows.forEach(row -> {
                row.setPlateNo(masker.exportPlate(row.getPlateNo()));
                row.setAppUserPhone(masker.exportPhone(row.getAppUserPhone()));
            });
        }
        XSSFWorkbook workbook = ExcelExportSupport.workbook();
        String[] headers = {"车辆ID", "车牌号", "VIN", "品牌", "型号", "颜色", "默认车辆", "归属用户", "用户手机号", "创建时间"};
        int[] widths = {8, 14, 20, 14, 16, 10, 10, 18, 16, 20};
        Sheet sheet = ExcelExportSupport.sheet(workbook, "车辆管理", headers, widths);
        int index = 1;
        for (VehicleVo vo : rows) {
            Row row = sheet.createRow(index++);
            int col = 0;
            row.createCell(col++).setCellValue(vo.getId() == null ? 0 : vo.getId());
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getPlateNo()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getVin()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getBrand()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getModel()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getColor()));
            row.createCell(col++).setCellValue("1".equals(vo.getIsDefault()) ? "是" : "否");
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getAppUserNickname()));
            row.createCell(col++).setCellValue(ExcelExportSupport.text(vo.getAppUserPhone()));
            row.createCell(col).setCellValue(ExcelExportSupport.time(vo.getCreateTime()));
        }
        ExcelExportSupport.finish(workbook, sheet, index - 1, headers.length, response, "vehicles");
    }

    private LambdaQueryWrapper<AppVehicle> buildAdminWrapper(VehicleQuery query) {
        List<Long> visibleUserIds = visibilityService.visibleUserIds();
        if (visibleUserIds != null && visibleUserIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<AppVehicle> wrapper = new LambdaQueryWrapper<>();
        if (visibleUserIds != null) {
            wrapper.in(AppVehicle::getAppUserId, visibleUserIds);
        }
        if (query.getAppUserId() != null) {
            visibilityService.assertVisible(query.getAppUserId());
            wrapper.eq(AppVehicle::getAppUserId, query.getAppUserId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AppVehicle::getPlateNo, keyword)
                    .or().like(AppVehicle::getBrand, keyword)
                    .or().like(AppVehicle::getModel, keyword));
        }
        wrapper.orderByDesc(AppVehicle::getId);
        return wrapper;
    }

    public List<VehicleVo> mine(Long userId) {
        return vehicleMapper.selectList(new LambdaQueryWrapper<AppVehicle>()
                        .eq(AppVehicle::getAppUserId, userId)
                        .orderByDesc(AppVehicle::getIsDefault)
                        .orderByDesc(AppVehicle::getId))
                .stream().map(VehicleVo::of).toList();
    }

    @Transactional
    public VehicleVo create(Long userId, VehicleRequest request) {
        requireUser(userId);
        String plateNo = normalizePlate(request.getPlateNo());
        ensurePlateUnique(userId, plateNo, null);
        String vin = normalizeVin(request.getVin());
        ensureVinUnique(vin, null);
        AppVehicle vehicle = new AppVehicle();
        vehicle.setAppUserId(userId);
        vehicle.setPlateNo(plateNo);
        vehicle.setVin(vin);
        vehicle.setBrand(trim(request.getBrand()));
        vehicle.setModel(trim(request.getModel()));
        vehicle.setColor(trim(request.getColor()));
        boolean first = vehicleMapper.selectCount(new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getAppUserId, userId)) == 0;
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || first;
        vehicle.setIsDefault(makeDefault ? "1" : "0");
        vehicleMapper.insert(vehicle);
        if (makeDefault) {
            clearDefault(userId, vehicle.getId());
        }
        return VehicleVo.of(vehicle);
    }

    @Transactional
    public VehicleVo update(Long id, Long userId, VehicleRequest request) {
        AppVehicle existing = requireOwned(id, userId);
        String plateNo = normalizePlate(request.getPlateNo());
        ensurePlateUnique(userId, plateNo, id);
        String vin = normalizeVin(request.getVin());
        ensureVinUnique(vin, id);
        existing.setPlateNo(plateNo);
        existing.setVin(vin);
        existing.setBrand(trim(request.getBrand()));
        existing.setModel(trim(request.getModel()));
        existing.setColor(trim(request.getColor()));
        if (request.getIsDefault() != null) {
            existing.setIsDefault(request.getIsDefault() ? "1" : "0");
        }
        vehicleMapper.updateById(existing);
        // updateById 忽略 null，清空 VIN 需要显式置空（与设备分组同一坑）
        if (vin == null) {
            vehicleMapper.update(null, new LambdaUpdateWrapper<AppVehicle>()
                    .eq(AppVehicle::getId, id)
                    .set(AppVehicle::getVin, null));
        }
        if ("1".equals(existing.getIsDefault())) {
            clearDefault(userId, id);
        }
        return VehicleVo.of(existing);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AppVehicle vehicle = requireOwned(id, userId);
        vehicleMapper.deleteById(id);
        if ("1".equals(vehicle.getIsDefault())) {
            promoteDefault(userId);
        }
    }

    @Transactional
    public void setDefault(Long id, Long userId) {
        AppVehicle vehicle = requireOwned(id, userId);
        vehicle.setIsDefault("1");
        vehicleMapper.updateById(vehicle);
        clearDefault(userId, id);
    }

    @Transactional
    public VehicleVo createAdmin(VehicleRequest request) {
        if (request.getAppUserId() == null) {
            throw new ServiceException(400, "请选择车辆归属用户");
        }
        visibilityService.assertVisible(request.getAppUserId());
        return create(request.getAppUserId(), request);
    }

    @Transactional
    public VehicleVo updateAdmin(Long id, VehicleRequest request) {
        AppVehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new ServiceException(404, "车辆不存在");
        }
        visibilityService.assertVisible(vehicle.getAppUserId());
        return update(id, vehicle.getAppUserId(), request);
    }

    @Transactional
    public void deleteAdmin(Long id) {
        AppVehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new ServiceException(404, "车辆不存在");
        }
        visibilityService.assertVisible(vehicle.getAppUserId());
        delete(id, vehicle.getAppUserId());
    }

    public AppVehicle requireOwned(Long id, Long userId) {
        AppVehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new ServiceException(404, "车辆不存在");
        }
        if (!Objects.equals(vehicle.getAppUserId(), userId)) {
            throw new ServiceException(403, "无权操作该车辆");
        }
        return vehicle;
    }

    public void validateVehicle(Long vehicleId, Long userId) {
        requireOwned(vehicleId, userId);
    }

    /**
     * 即插即充按 VIN 反查车辆：格式不合法或多条命中（数据异常）都返回 null，
     * 由调用方决定拒绝，不抛异常打断设备报文处理。
     */
    public AppVehicle findByVin(String vin) {
        if (!StringUtils.hasText(vin)) {
            return null;
        }
        String value = vin.trim().toUpperCase(Locale.ROOT);
        if (!VIN_PATTERN.matcher(value).matches()) {
            return null;
        }
        List<AppVehicle> matches = vehicleMapper.selectList(new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getVin, value));
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<VehicleVo> toVos(List<AppVehicle> vehicles) {
        if (vehicles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, AppUser> users = userMapper.selectBatchIds(
                        vehicles.stream().map(AppVehicle::getAppUserId).distinct().toList())
                .stream().collect(Collectors.toMap(AppUser::getId, item -> item));
        return vehicles.stream().map(vehicle -> {
            VehicleVo vo = VehicleVo.of(vehicle);
            AppUser user = users.get(vehicle.getAppUserId());
            if (user != null) {
                vo.setAppUserNickname(user.getNickname());
                vo.setAppUserPhone(user.getPhone());
            }
            return vo;
        }).toList();
    }

    private void requireUser(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ServiceException(404, "小程序用户不存在");
        }
    }

    private void ensurePlateUnique(Long userId, String plateNo, Long excludeId) {
        LambdaQueryWrapper<AppVehicle> wrapper = new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getAppUserId, userId)
                .eq(AppVehicle::getPlateNo, plateNo);
        if (excludeId != null) {
            wrapper.ne(AppVehicle::getId, excludeId);
        }
        if (vehicleMapper.selectCount(wrapper) > 0) {
            throw new ServiceException(400, "该车牌已绑定到此用户");
        }
    }

    private void clearDefault(Long userId, Long exceptId) {
        vehicleMapper.update(null, new LambdaUpdateWrapper<AppVehicle>()
                .eq(AppVehicle::getAppUserId, userId)
                .ne(AppVehicle::getId, exceptId)
                .set(AppVehicle::getIsDefault, "0"));
    }

    private void promoteDefault(Long userId) {
        AppVehicle latest = vehicleMapper.selectOne(new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getAppUserId, userId)
                .orderByDesc(AppVehicle::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            latest.setIsDefault("1");
            vehicleMapper.updateById(latest);
        }
    }

    private String normalizePlate(String plateNo) {
        return plateNo.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private String normalizeVin(String vin) {
        if (!StringUtils.hasText(vin)) {
            return null;
        }
        String value = vin.trim().toUpperCase(Locale.ROOT);
        if (!VIN_PATTERN.matcher(value).matches()) {
            throw new ServiceException(400, "VIN 必须为 17 位，且不含 I、O、Q");
        }
        return value;
    }

    private void ensureVinUnique(String vin, Long excludeId) {
        if (vin == null) {
            return;
        }
        LambdaQueryWrapper<AppVehicle> wrapper = new LambdaQueryWrapper<AppVehicle>()
                .eq(AppVehicle::getVin, vin);
        if (excludeId != null) {
            wrapper.ne(AppVehicle::getId, excludeId);
        }
        if (vehicleMapper.selectCount(wrapper) > 0) {
            throw new ServiceException(400, "该 VIN 已绑定到其他车辆");
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
