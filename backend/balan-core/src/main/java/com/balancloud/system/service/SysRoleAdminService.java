package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.tenant.TenantContext;
import com.balancloud.framework.security.TokenService;
import com.balancloud.spi.TenantMenuScope;
import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.domain.SysRole;
import com.balancloud.system.dto.RoleRequest;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysRoleMenuMapper;
import com.balancloud.system.mapper.SysUserRoleMapper;
import com.balancloud.system.vo.GrantMenuVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色管理与菜单授权。读侧在 {@link SysRoleQueryService}，这里只负责写。
 */
@Service
public class SysRoleAdminService {

    private static final Long SUPER_ADMIN_ROLE_ID = 1L;
    private static final String BUILTIN = "1";
    private static final String STATUS_NORMAL = "0";

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final TokenService tokenService;
    private final TenantMenuScope tenantMenuScope;

    public SysRoleAdminService(SysRoleMapper roleMapper,
                               SysMenuMapper menuMapper,
                               SysRoleMenuMapper roleMenuMapper,
                               SysUserRoleMapper userRoleMapper,
                               TokenService tokenService,
                               TenantMenuScope tenantMenuScope) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.tokenService = tokenService;
        this.tenantMenuScope = tenantMenuScope;
    }

    /**
     * 可授权菜单树，含按钮（F）节点，与菜单管理页选父节点用的 /options 不同。
     * 套餐裁剪发生在 {@link TenantMenuScope} 上：社区版不裁剪，标准版只留套餐里的节点。
     */
    public List<GrantMenuVo> grantableMenuTree() {
        List<SysMenu> all = withinMenuScope(selectAllMenus());
        Map<Long, GrantMenuVo> nodes = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            nodes.put(menu.getMenuId(), toNode(menu));
        }
        List<GrantMenuVo> roots = new ArrayList<>();
        for (SysMenu menu : all) {
            GrantMenuVo node = nodes.get(menu.getMenuId());
            GrantMenuVo parent = menu.getParentId() == null ? null : nodes.get(menu.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Transactional
    public Long create(RoleRequest request) {
        requireUniqueRoleKey(request.getRoleKey(), null);
        SysRole role = new SysRole();
        BeanUtils.copyProperties(request, role);
        role.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_NORMAL);
        role.setBuiltin("0");
        roleMapper.insert(role);
        if (request.getMenuIds() != null) {
            replaceMenus(role.getRoleId(), request.getMenuIds());
        }
        return role.getRoleId();
    }

    @Transactional
    public void update(Long roleId, RoleRequest request) {
        SysRole current = requireRole(roleId);
        boolean keyChanged = !current.getRoleKey().equals(request.getRoleKey());
        if (keyChanged) {
            if (BUILTIN.equals(current.getBuiltin())) {
                throw new ServiceException("内置角色的角色标识不允许修改，改名请用角色名称");
            }
            requireUniqueRoleKey(request.getRoleKey(), roleId);
        }

        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleName(request.getRoleName());
        role.setRoleKey(request.getRoleKey());
        role.setRoleSort(request.getRoleSort());
        role.setRemark(request.getRemark());
        boolean statusChanged = StringUtils.hasText(request.getStatus())
                && !request.getStatus().equals(current.getStatus());
        if (statusChanged) {
            requireNotSuperAdmin(roleId, "内置超级管理员角色不允许停用");
            role.setStatus(request.getStatus());
        }
        roleMapper.updateById(role);

        if (keyChanged || statusChanged) {
            evictRoleUsers(roleId);
        }
    }

    @Transactional
    public void changeStatus(Long roleId, String status) {
        if (!STATUS_NORMAL.equals(status) && !"1".equals(status)) {
            throw new ServiceException("角色状态只允许 0（正常）或 1（停用）");
        }
        SysRole current = requireRole(roleId);
        if (!STATUS_NORMAL.equals(status)) {
            requireNotSuperAdmin(roleId, "内置超级管理员角色不允许停用");
        }
        if (status.equals(current.getStatus())) {
            return;
        }
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setStatus(status);
        roleMapper.updateById(role);
        evictRoleUsers(roleId);
    }

    @Transactional
    public void grantMenus(Long roleId, List<Long> menuIds) {
        requireRole(roleId);
        replaceMenus(roleId, menuIds);
        evictRoleUsers(roleId);
    }

    @Transactional
    public void delete(Long roleId) {
        SysRole role = requireRole(roleId);
        if (BUILTIN.equals(role.getBuiltin())) {
            throw new ServiceException("内置角色「" + role.getRoleName() + "」不允许删除");
        }
        int userCount = userRoleMapper.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new ServiceException("该角色下还有 " + userCount + " 个账号，请先调整账号的角色");
        }
        roleMapper.deleteById(roleId);
        // sys_role_menu 必须物理清除，否则 SysMenuAdminService 的「菜单已分配给角色」占用数永远降不下来。
        roleMenuMapper.deleteByRoleId(roleId);
    }

    /**
     * 全量覆盖授权，并补齐勾选节点的祖先：侧边栏按父子链渲染，只存叶子会让菜单「授权了但显示不出来」。
     */
    private void replaceMenus(Long roleId, List<Long> menuIds) {
        requireNotSuperAdmin(roleId, "内置超级管理员角色的权限不允许在角色授权里修改");
        assertWithinMenuScope(menuIds);
        List<SysMenu> all = selectAllMenus();
        Set<Long> normalized = withAncestors(menuIds, all);
        roleMenuMapper.deleteByRoleId(roleId);
        for (Long menuId : normalized) {
            roleMenuMapper.insert(roleId, menuId);
        }
    }

    /** 套餐裁剪在授权写入的唯一入口上挡住：租户管理员给自己加一个套餐外的菜单是不存在的操作。 */
    private void assertWithinMenuScope(List<Long> menuIds) {
        Set<Long> allowed = tenantMenuScope.allowedMenuIds(TenantContext.get());
        if (allowed == null) {
            return;
        }
        for (Long menuId : menuIds == null ? List.<Long>of() : menuIds) {
            if (menuId != null && !allowed.contains(menuId)) {
                throw new ServiceException("菜单 " + menuId + " 不在当前租户的套餐内，无法授权");
            }
        }
    }

    /** 裁剪只剪掉套餐外的节点；目录若整棵被剪掉，剩下的子节点按顶级菜单呈现，不会丢。 */
    private List<SysMenu> withinMenuScope(List<SysMenu> menus) {
        Set<Long> allowed = tenantMenuScope.allowedMenuIds(TenantContext.get());
        if (allowed == null) {
            return menus;
        }
        return menus.stream().filter(menu -> allowed.contains(menu.getMenuId())).toList();
    }

    static Set<Long> withAncestors(List<Long> menuIds, List<SysMenu> allMenus) {
        Map<Long, SysMenu> byId = allMenus.stream()
                .collect(Collectors.toMap(SysMenu::getMenuId, Function.identity(), (left, right) -> left));
        Set<Long> granted = new LinkedHashSet<>();
        for (Long menuId : menuIds == null ? List.<Long>of() : menuIds) {
            SysMenu menu = byId.get(menuId);
            if (menu == null) {
                throw new ServiceException("权限项 " + menuId + " 不存在，请刷新后重新勾选");
            }
            granted.add(menuId);
            SysMenu parent = byId.get(menu.getParentId());
            while (parent != null && granted.add(parent.getMenuId())) {
                parent = byId.get(parent.getParentId());
            }
        }
        return granted;
    }

    private List<SysMenu> selectAllMenus() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum)
                .orderByAsc(SysMenu::getMenuId);
        return menuMapper.selectList(wrapper);
    }

    private GrantMenuVo toNode(SysMenu menu) {
        GrantMenuVo node = new GrantMenuVo();
        node.setId(menu.getMenuId());
        node.setLabel(menu.getMenuName());
        node.setMenuType(menu.getMenuType());
        node.setPerms(menu.getPerms());
        return node;
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException(404, "角色不存在");
        }
        return role;
    }

    private void requireNotSuperAdmin(Long roleId, String message) {
        if (SUPER_ADMIN_ROLE_ID.equals(roleId)) {
            throw new ServiceException(message);
        }
    }

    /**
     * role_key 的唯一键不含 del_flag，所以逻辑删除的角色仍占着标识，提示口径要与库一致。
     */
    private void requireUniqueRoleKey(String roleKey, Long excludeRoleId) {
        for (Long existingId : roleMapper.selectRoleIdsByKeyIncludeDeleted(roleKey)) {
            if (!existingId.equals(excludeRoleId)) {
                throw new ServiceException("角色标识 " + roleKey + " 已存在（含已删除角色）");
            }
        }
    }

    private void evictRoleUsers(Long roleId) {
        for (Long userId : userRoleMapper.selectListByRoleId(roleId)) {
            tokenService.evictUser(userId);
        }
    }
}
