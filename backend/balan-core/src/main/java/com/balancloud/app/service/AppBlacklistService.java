package com.balancloud.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.app.domain.AppBlacklist;
import com.balancloud.app.domain.AppUser;
import com.balancloud.app.domain.AppVehicle;
import com.balancloud.app.dto.BlacklistQuery;
import com.balancloud.app.dto.BlacklistRequest;
import com.balancloud.app.dto.BlacklistUpdateRequest;
import com.balancloud.app.mapper.AppBlacklistMapper;
import com.balancloud.app.mapper.AppUserMapper;
import com.balancloud.app.mapper.AppVehicleMapper;
import com.balancloud.app.vo.BlacklistVo;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.framework.sensitive.SensitiveDataMasker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 黑名单轻量版（core）：用户维度与车牌维度拉黑，充电启动前拦截。
 * 信用分、逃单联动与风控策略池属标准版 jar。
 */
@Service
public class AppBlacklistService {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_PLATE = "PLATE";

    private static final Pattern PLATE_NO = Pattern.compile("^[A-Z0-9\\u4e00-\\u9fa5]{4,12}$");

    private final AppBlacklistMapper blacklistMapper;
    private final AppUserMapper userMapper;
    private final AppVehicleMapper vehicleMapper;
    private final AppUserVisibilityService visibilityService;
    private final SensitiveDataMasker masker;

    public AppBlacklistService(AppBlacklistMapper blacklistMapper,
                               AppUserMapper userMapper,
                               AppVehicleMapper vehicleMapper,
                               AppUserVisibilityService visibilityService,
                               SensitiveDataMasker masker) {
        this.blacklistMapper = blacklistMapper;
        this.userMapper = userMapper;
        this.vehicleMapper = vehicleMapper;
        this.visibilityService = visibilityService;
        this.masker = masker;
    }

    public TableDataInfo list(BlacklistQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<AppBlacklist> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AppBlacklist::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getScopeType())) {
            wrapper.eq(AppBlacklist::getScopeType, query.getScopeType().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<AppUser>()
                            .select(AppUser::getId)
                            .and(w -> w.like(AppUser::getNickname, keyword)
                                    .or().like(AppUser::getPhone, keyword)))
                    .stream().map(AppUser::getId).toList();
            wrapper.and(w -> {
                w.like(AppBlacklist::getPlateNo, keyword);
                if (!userIds.isEmpty()) {
                    w.or().in(AppBlacklist::getAppUserId, userIds);
                }
            });
        }
        List<Long> visibleUserIds = visibilityService.visibleUserIds();
        if (visibleUserIds != null) {
            if (visibleUserIds.isEmpty()) {
                wrapper.eq(AppBlacklist::getScopeType, SCOPE_PLATE);
            } else {
                wrapper.and(w -> w.eq(AppBlacklist::getScopeType, SCOPE_PLATE)
                        .or().in(AppBlacklist::getAppUserId, visibleUserIds));
            }
        }
        wrapper.orderByAsc(AppBlacklist::getStatus).orderByDesc(AppBlacklist::getId);
        Page<AppBlacklist> page = blacklistMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BlacklistVo> rows = toVos(page.getRecords());
        if (masker.displayEnabled()) {
            rows.forEach(row -> {
                row.setPhone(masker.displayPhone(row.getPhone()));
                row.setPlateNo(masker.displayPlate(row.getPlateNo()));
            });
        }
        return TableDataInfo.of(page.getTotal(), rows);
    }

    public List<AppUser> userOptions(String keyword) {
        List<Long> visibleUserIds = visibilityService.visibleUserIds();
        if (visibleUserIds != null && visibleUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .select(AppUser::getId, AppUser::getNickname, AppUser::getPhone, AppUser::getStatus);
        if (visibleUserIds != null) {
            wrapper.in(AppUser::getId, visibleUserIds);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(AppUser::getNickname, value).or().like(AppUser::getPhone, value));
        }
        wrapper.orderByDesc(AppUser::getId).last("LIMIT 20");
        List<AppUser> users = userMapper.selectList(wrapper);
        if (masker.displayEnabled()) {
            users.forEach(user -> user.setPhone(masker.displayPhone(user.getPhone())));
        }
        return users;
    }

    @Transactional
    public void create(BlacklistRequest request) {
        String scope = normalizeScope(request.getScopeType());
        String reason = requireReason(request.getReason());
        LocalDateTime expireAt = checkExpire(request.getExpireAt());
        AppBlacklist entry = new AppBlacklist();
        entry.setScopeType(scope);
        entry.setReason(reason);
        entry.setExpireAt(expireAt);
        if (SCOPE_USER.equals(scope)) {
            if (request.getAppUserId() == null) {
                throw new ServiceException(400, "用户维度黑名单必须选择小程序用户");
            }
            AppUser user = userMapper.selectById(request.getAppUserId());
            if (user == null) {
                throw new ServiceException(404, "小程序用户不存在");
            }
            visibilityService.assertVisible(request.getAppUserId());
            ensureNoDuplicate(scope, request.getAppUserId(), null, null);
            entry.setAppUserId(request.getAppUserId());
        } else {
            String plateNo = normalizePlate(request.getPlateNo());
            ensureNoDuplicate(scope, null, plateNo, null);
            entry.setPlateNo(plateNo);
        }
        entry.setStatus("0");
        entry.setCreateBy(SecurityUtils.getUsername());
        entry.setCreateTime(LocalDateTime.now());
        entry.setUpdateBy(SecurityUtils.getUsername());
        entry.setUpdateTime(LocalDateTime.now());
        blacklistMapper.insert(entry);
    }

    @Transactional
    public void update(Long id, BlacklistUpdateRequest request) {
        AppBlacklist entry = requireEntry(id);
        String reason = StringUtils.hasText(request.getReason()) ? requireReason(request.getReason()) : entry.getReason();
        LocalDateTime expireAt = request.getExpireAt() == null
                ? entry.getExpireAt() : checkExpire(request.getExpireAt());
        String status = entry.getStatus();
        if (StringUtils.hasText(request.getStatus())) {
            status = request.getStatus().trim();
            if (!"0".equals(status) && !"1".equals(status)) {
                throw new ServiceException(400, "黑名单状态不合法");
            }
        }
        if ("0".equals(status) && !"0".equals(entry.getStatus())) {
            ensureNoDuplicate(entry.getScopeType(), entry.getAppUserId(), entry.getPlateNo(), id);
        }
        blacklistMapper.update(null, new LambdaUpdateWrapper<AppBlacklist>()
                .eq(AppBlacklist::getId, id)
                .set(AppBlacklist::getReason, reason)
                .set(AppBlacklist::getExpireAt, expireAt)
                .set(AppBlacklist::getStatus, status)
                .set(AppBlacklist::getUpdateBy, SecurityUtils.getUsername())
                .set(AppBlacklist::getUpdateTime, LocalDateTime.now()));
    }

    @Transactional
    public void delete(Long id) {
        requireEntry(id);
        blacklistMapper.deleteById(id);
    }

    /**
     * 小程序启动充电前调用：账号停用、用户黑名单、所选车辆车牌黑名单三重校验。
     */
    public void assertNotBlocked(AppUser user, Long vehicleId) {
        if (user == null) {
            throw new ServiceException(401, "登录状态无效，请重新登录");
        }
        if ("1".equals(user.getStatus())) {
            throw new ServiceException(403, "账号已停用，请联系客服处理");
        }
        if (hasActive(SCOPE_USER, user.getId(), null)) {
            throw new ServiceException(403, "账号已被列入充电黑名单，如有疑问请联系客服");
        }
        if (vehicleId != null) {
            AppVehicle vehicle = vehicleMapper.selectById(vehicleId);
            if (vehicle != null && StringUtils.hasText(vehicle.getPlateNo())
                    && hasActive(SCOPE_PLATE, null, normalizePlate(vehicle.getPlateNo()))) {
                throw new ServiceException(403,
                        "车牌 " + vehicle.getPlateNo() + " 已被列入充电黑名单，如有疑问请联系客服");
            }
        }
    }

    private boolean hasActive(String scope, Long userId, String plateNo) {
        LambdaQueryWrapper<AppBlacklist> wrapper = new LambdaQueryWrapper<AppBlacklist>()
                .eq(AppBlacklist::getScopeType, scope)
                .eq(AppBlacklist::getStatus, "0")
                .and(w -> w.isNull(AppBlacklist::getExpireAt)
                        .or().gt(AppBlacklist::getExpireAt, LocalDateTime.now()));
        if (SCOPE_USER.equals(scope)) {
            wrapper.eq(AppBlacklist::getAppUserId, userId);
        } else {
            wrapper.eq(AppBlacklist::getPlateNo, plateNo);
        }
        return blacklistMapper.selectCount(wrapper) > 0;
    }

    private void ensureNoDuplicate(String scope, Long userId, String plateNo, Long excludeId) {
        LambdaQueryWrapper<AppBlacklist> wrapper = new LambdaQueryWrapper<AppBlacklist>()
                .eq(AppBlacklist::getScopeType, scope)
                .eq(AppBlacklist::getStatus, "0")
                .and(w -> w.isNull(AppBlacklist::getExpireAt)
                        .or().gt(AppBlacklist::getExpireAt, LocalDateTime.now()));
        if (SCOPE_USER.equals(scope)) {
            wrapper.eq(AppBlacklist::getAppUserId, userId);
        } else {
            wrapper.eq(AppBlacklist::getPlateNo, plateNo);
        }
        if (excludeId != null) {
            wrapper.ne(AppBlacklist::getId, excludeId);
        }
        if (blacklistMapper.selectCount(wrapper) > 0) {
            throw new ServiceException(409, SCOPE_USER.equals(scope)
                    ? "该用户已在生效中的黑名单里" : "该车牌已在生效中的黑名单里");
        }
    }

    private List<BlacklistVo> toVos(List<AppBlacklist> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = entries.stream().map(AppBlacklist::getAppUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, AppUser> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return entries.stream().map(entry -> {
            BlacklistVo vo = new BlacklistVo();
            vo.setId(entry.getId());
            vo.setScopeType(entry.getScopeType());
            vo.setAppUserId(entry.getAppUserId());
            vo.setPlateNo(entry.getPlateNo());
            vo.setReason(entry.getReason());
            vo.setStatus(entry.getStatus());
            vo.setExpireAt(entry.getExpireAt());
            vo.setExpired(entry.getExpireAt() != null && !entry.getExpireAt().isAfter(now));
            vo.setCreateBy(entry.getCreateBy());
            vo.setCreateTime(entry.getCreateTime());
            vo.setUpdateBy(entry.getUpdateBy());
            vo.setUpdateTime(entry.getUpdateTime());
            AppUser user = entry.getAppUserId() == null ? null : users.get(entry.getAppUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setPhone(user.getPhone());
            }
            return vo;
        }).toList();
    }

    private AppBlacklist requireEntry(Long id) {
        AppBlacklist entry = blacklistMapper.selectById(id);
        if (entry == null) {
            throw new ServiceException(404, "黑名单记录不存在");
        }
        if (SCOPE_USER.equals(entry.getScopeType()) && entry.getAppUserId() != null) {
            visibilityService.assertVisible(entry.getAppUserId());
        }
        return entry;
    }

    private String normalizeScope(String value) {
        String scope = value == null ? "" : value.trim().toUpperCase();
        if (!SCOPE_USER.equals(scope) && !SCOPE_PLATE.equals(scope)) {
            throw new ServiceException(400, "黑名单维度只支持 USER 或 PLATE");
        }
        return scope;
    }

    private String normalizePlate(String value) {
        String plate = value == null ? "" : value.trim().replace(" ", "").toUpperCase();
        if (!PLATE_NO.matcher(plate).matches()) {
            throw new ServiceException(400, "车牌号格式不合法（4~12 位字母、数字或汉字）");
        }
        return plate;
    }

    private String requireReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (!StringUtils.hasText(reason)) {
            throw new ServiceException(400, "拉黑原因不能为空");
        }
        if (reason.length() > 200) {
            throw new ServiceException(400, "拉黑原因不能超过 200 个字符");
        }
        return reason;
    }

    private LocalDateTime checkExpire(LocalDateTime expireAt) {
        if (expireAt != null && !expireAt.isAfter(LocalDateTime.now())) {
            throw new ServiceException(400, "失效时间必须晚于当前时间");
        }
        return expireAt;
    }
}
