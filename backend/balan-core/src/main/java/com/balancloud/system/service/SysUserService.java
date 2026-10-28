package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.balancloud.common.core.TableDataInfo;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.common.utils.SecurityUtils;
import com.balancloud.device.domain.Station;
import com.balancloud.device.mapper.StationMapper;
import com.balancloud.framework.security.LoginUser;
import com.balancloud.framework.security.TokenService;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.framework.tenant.mapper.TenantDirectoryMapper;
import com.balancloud.system.domain.SysRole;
import com.balancloud.system.domain.SysUser;
import com.balancloud.system.dto.ProfileUpdateRequest;
import com.balancloud.system.dto.UserQuery;
import com.balancloud.system.dto.UserRequest;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysUserMapper;
import com.balancloud.system.mapper.SysUserRoleMapper;
import com.balancloud.system.mapper.SysUserStationMapper;
import com.balancloud.system.vo.ProfileVo;
import com.balancloud.system.vo.SystemUserVo;
import com.balancloud.system.vo.UserRoleRow;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysUserService {

    private static final long SUPER_ADMIN_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserStationMapper userStationMapper;
    private final SysRoleMapper roleMapper;
    private final StationMapper stationMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TenantDirectoryMapper tenantDirectoryMapper;

    public SysUserService(SysUserMapper userMapper,
                          SysUserRoleMapper userRoleMapper,
                          SysUserStationMapper userStationMapper,
                          SysRoleMapper roleMapper,
                          StationMapper stationMapper,
                          PasswordEncoder passwordEncoder,
                          TokenService tokenService,
                          TenantDirectoryMapper tenantDirectoryMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userStationMapper = userStationMapper;
        this.roleMapper = roleMapper;
        this.stationMapper = stationMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tenantDirectoryMapper = tenantDirectoryMapper;
    }

    public TableDataInfo list(UserQuery query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUserName, keyword)
                    .or().like(SysUser::getNickName, keyword)
                    .or().like(SysUser::getEmail, keyword)
                    .or().like(SysUser::getPhoneNumber, keyword));
        }
        wrapper.eq(StringUtils.hasText(query.getStatus()), SysUser::getStatus, query.getStatus());
        wrapper.ge(query.getBeginTime() != null, SysUser::getCreateTime, query.getBeginTime());
        wrapper.le(query.getEndTime() != null, SysUser::getCreateTime, query.getEndTime());
        if (query.getRoleId() != null) {
            List<Long> userIds = userRoleMapper.selectListByRoleId(query.getRoleId());
            if (userIds.isEmpty()) {
                return TableDataInfo.of(0, Collections.emptyList());
            }
            wrapper.in(SysUser::getUserId, userIds);
        }
        wrapper.orderByAsc(SysUser::getUserId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SystemUserVo> rows = page.getRecords().stream().map(this::toVo).toList();
        return TableDataInfo.of(page.getTotal(), rows);
    }

    public SystemUserVo detail(Long userId) {
        return toVo(requireUser(userId));
    }

    /**
     * 个人中心：当前账号自己的资料 + 归属运营商名称。
     * 部门/岗位在 sys_user 里没有对应列，所以这里给的是租户，不要照着 RuoYi 的字段清单去补。
     */
    public ProfileVo profile() {
        SysUser user = requireUser(SecurityUtils.getUserId());
        ProfileVo vo = new ProfileVo();
        vo.setUser(toVo(user));
        vo.setTenantName(tenantDirectoryMapper.selectTenantNameById(currentTenantId()));
        return vo;
    }

    @Transactional
    public void updateProfile(ProfileUpdateRequest request) {
        Long userId = SecurityUtils.getUserId();
        requireUser(userId);
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setNickName(request.getNickName().trim());
        update.setEmail(defaultEmpty(request.getEmail()));
        update.setPhoneNumber(defaultEmpty(request.getPhoneNumber()));
        userMapper.updateById(update);
    }

    private Long currentTenantId() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser == null || loginUser.getTenantId() == null
                ? TenantContext.DEFAULT_TENANT_ID
                : loginUser.getTenantId();
    }

    @Transactional
    public Long create(UserRequest request) {
        String userName = request.getUserName().trim();
        if (!StringUtils.hasText(request.getPassword())) {
            throw new ServiceException("新增账号必须设置初始密码");
        }
        if (userMapper.countByUserNameIgnoreDel(userName) > 0) {
            throw new ServiceException("登录名 " + userName + " 已存在（含已删除账号）");
        }
        List<Long> roleIds = requireRoleIds(request.getRoleIds());
        List<Long> stationIds = normalizeStationIds(request.getStationIds());

        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setNickName(request.getNickName().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(defaultEmpty(request.getEmail()));
        user.setPhoneNumber(defaultEmpty(request.getPhoneNumber()));
        user.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "0");
        user.setRemark(request.getRemark());
        user.setAvatar("");
        user.setLoginIp("");
        user.setPwdResetRequired("1");
        userMapper.insert(user);

        roleIds.forEach(roleId -> userRoleMapper.insert(user.getUserId(), roleId));
        stationIds.forEach(stationId -> userStationMapper.insert(user.getUserId(), stationId));
        return user.getUserId();
    }

    @Transactional
    public void update(Long userId, UserRequest request) {
        SysUser user = requireUser(userId);
        boolean grantEditable = !isSelf(userId) && user.getUserId() != SUPER_ADMIN_ID;

        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setNickName(request.getNickName().trim());
        update.setEmail(defaultEmpty(request.getEmail()));
        update.setPhoneNumber(defaultEmpty(request.getPhoneNumber()));
        update.setRemark(request.getRemark());
        if (grantEditable && StringUtils.hasText(request.getStatus())) {
            update.setStatus(request.getStatus());
        }
        userMapper.updateById(update);

        if (grantEditable) {
            List<Long> oldRoleIds = userRoleMapper.selectRoleIdsByUserId(userId);
            List<Long> oldStationIds = userStationMapper.selectStationIdsByUserId(userId);
            List<Long> roleIds = requireRoleIds(request.getRoleIds());
            List<Long> stationIds = normalizeStationIds(request.getStationIds());
            userRoleMapper.deleteByUserId(userId);
            roleIds.forEach(roleId -> userRoleMapper.insert(userId, roleId));
            userStationMapper.deleteByUserId(userId);
            stationIds.forEach(stationId -> userStationMapper.insert(userId, stationId));

            boolean grantChanged = !oldRoleIds.containsAll(roleIds) || !roleIds.containsAll(oldRoleIds)
                    || !oldStationIds.containsAll(stationIds) || !stationIds.containsAll(oldStationIds);
            if (grantChanged) {
                // 会话里的角色与场站范围是登录时快照，变更后必须重新登录才生效
                tokenService.evictUser(userId);
            }
        }

        if (!Objects.equals(user.getStatus(), update.getStatus()) && "1".equals(update.getStatus())) {
            tokenService.evictUser(userId);
        }
    }

    @Transactional
    public void changeStatus(Long userId, String status) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException("账号状态不合法");
        }
        SysUser user = requireUser(userId);
        if (user.getUserId() == SUPER_ADMIN_ID) {
            throw new ServiceException("内置超级管理员账号不允许停用");
        }
        if (isSelf(userId)) {
            throw new ServiceException("不能停用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        if ("1".equals(status)) {
            tokenService.evictUser(userId);
        }
    }

    public void resetPassword(Long userId, String rawPassword) {
        requireUser(userId);
        if (isSelf(userId)) {
            throw new ServiceException("不能重置当前登录账号的密码，请退出后使用登录页找回");
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(rawPassword));
        update.setPwdUpdateDate(LocalDateTime.now());
        update.setPwdResetRequired("1");
        userMapper.updateById(update);
        tokenService.evictUser(userId);
    }

    @Transactional
    public void remove(Long userId) {
        SysUser user = requireUser(userId);
        if (user.getUserId() == SUPER_ADMIN_ID) {
            throw new ServiceException("内置超级管理员账号不允许删除");
        }
        if (isSelf(userId)) {
            throw new ServiceException("不能删除当前登录账号");
        }
        userMapper.deleteById(userId);
        userRoleMapper.deleteByUserId(userId);
        userStationMapper.deleteByUserId(userId);
        tokenService.evictUser(userId);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(404, "账号不存在");
        }
        return user;
    }

    private List<Long> requireRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new ServiceException("至少为账号分配一个角色");
        }
        Set<Long> distinct = new LinkedHashSet<>(roleIds);
        List<SysRole> roles = roleMapper.selectBatchIds(distinct);
        if (roles.size() != distinct.size()) {
            throw new ServiceException("包含不存在的角色，请刷新后重试");
        }
        return new ArrayList<>(distinct);
    }

    private List<Long> normalizeStationIds(List<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> distinct = new LinkedHashSet<>(stationIds.stream().filter(Objects::nonNull).toList());
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<Station> stations = stationMapper.selectBatchIds(distinct);
        if (stations.size() != distinct.size()) {
            throw new ServiceException("包含不存在或已删除的场站");
        }
        return new ArrayList<>(distinct);
    }

    private boolean isSelf(Long userId) {
        Long currentUserId = SecurityUtils.getUserId();
        return currentUserId != null && currentUserId.equals(userId);
    }

    private String defaultEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private SystemUserVo toVo(SysUser user) {
        SystemUserVo vo = new SystemUserVo();
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUserName());
        vo.setNickName(user.getNickName());
        vo.setEmail(user.getEmail());
        vo.setPhoneNumber(user.getPhoneNumber());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        vo.setLoginIp(user.getLoginIp());
        vo.setLoginDate(user.getLoginDate());
        vo.setPwdUpdateDate(user.getPwdUpdateDate());
        vo.setPwdResetRequired(user.getPwdResetRequired());
        vo.setCreateTime(user.getCreateTime());
        List<UserRoleRow> roles = userRoleMapper.selectRolesByUserId(user.getUserId());
        vo.setRoleIds(roles.stream().map(UserRoleRow::getRoleId).collect(Collectors.toList()));
        vo.setRoleNames(roles.stream().map(UserRoleRow::getRoleName).collect(Collectors.toList()));
        vo.setStationIds(userStationMapper.selectStationIdsByUserId(user.getUserId()));
        return vo;
    }
}
