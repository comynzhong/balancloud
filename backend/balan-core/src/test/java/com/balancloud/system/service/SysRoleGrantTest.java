package com.balancloud.system.service;

import com.balancloud.common.exception.ServiceException;
import com.balancloud.framework.security.TokenService;
import com.balancloud.spi.defaults.UnrestrictedTenantMenuScope;
import com.balancloud.support.TableInfoSupport;
import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.domain.SysRole;
import com.balancloud.system.dto.RoleRequest;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysRoleMenuMapper;
import com.balancloud.system.mapper.SysUserRoleMapper;
import com.balancloud.system.vo.GrantMenuVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A1 角色授权：勾选规范化（祖先补全）、内置角色保护与即时失效。
 * 三条菜单查询在同空同非空上的表现依赖真实库，需起服务后用接口回归验证。
 */
class SysRoleGrantTest {

    private SysRoleMapper roleMapper;
    private SysMenuMapper menuMapper;
    private SysRoleMenuMapper roleMenuMapper;
    private SysUserRoleMapper userRoleMapper;
    private TokenService tokenService;
    private SysRoleAdminService service;

    private static final List<SysMenu> MENUS = List.of(
            menu(5L, 0L, "系统管理", "M"),
            menu(510L, 5L, "角色管理", "C"),
            menu(511L, 510L, "角色查询", "F"),
            menu(553L, 510L, "角色授权", "F"),
            menu(540L, 5L, "菜单管理", "C"));

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoSupport.init(SysMenu.class, SysRole.class);
    }

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        menuMapper = mock(SysMenuMapper.class);
        roleMenuMapper = mock(SysRoleMenuMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        tokenService = mock(TokenService.class);
        service = new SysRoleAdminService(roleMapper, menuMapper, roleMenuMapper, userRoleMapper, tokenService,
                new UnrestrictedTenantMenuScope());
        when(menuMapper.selectList(any())).thenReturn(MENUS);
    }

    @Test
    void grantsCheckedButtonTogetherWithItsAncestors() {
        Set<Long> normalized = SysRoleAdminService.withAncestors(List.of(553L), MENUS);

        assertThat(normalized).containsExactlyInAnyOrder(553L, 510L, 5L);
    }

    @Test
    void ancestorCompletionIsIdempotentAndKeepsSiblings() {
        Set<Long> normalized = SysRoleAdminService.withAncestors(List.of(553L, 510L, 5L, 511L, 511L), MENUS);

        assertThat(normalized).containsExactlyInAnyOrder(5L, 510L, 511L, 553L);
    }

    @Test
    void rejectsUnknownMenuIdInsteadOfSilentlyDroppingIt() {
        assertThatThrownBy(() -> SysRoleAdminService.withAncestors(List.of(99999L), MENUS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("99999");
    }

    @Test
    void replacingGrantsWritesNormalizedRowsAndEvictsRoleUsers() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "operator", "0"));
        when(userRoleMapper.selectListByRoleId(2L)).thenReturn(List.of(7L, 8L));

        service.grantMenus(2L, List.of(553L));

        ArgumentCaptor<Long> inserted = ArgumentCaptor.forClass(Long.class);
        verify(roleMenuMapper).deleteByRoleId(2L);
        verify(roleMenuMapper, times(3)).insert(eq(2L), inserted.capture());
        assertThat(inserted.getAllValues()).containsExactlyInAnyOrder(553L, 510L, 5L);
        verify(tokenService).evictUser(7L);
        verify(tokenService).evictUser(8L);
    }

    @Test
    void builtinRoleKeyIsReadOnly() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "operator", "1"));
        RoleRequest request = request("场站运营", "operator_v2");

        assertThatThrownBy(() -> service.update(2L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("内置角色的角色标识不允许修改");
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void profileOnlyUpdateDoesNotEvictSessions() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "finance", "0"));

        service.update(6L, request("财务", "finance"));

        verify(tokenService, never()).evictUser(any());
        verify(userRoleMapper, never()).selectListByRoleId(any());
    }

    @Test
    void duplicateRoleKeyIsRejectedIncludingDeletedRoles() {
        when(roleMapper.selectRoleIdsByKeyIncludeDeleted("finance")).thenReturn(List.of(9L));

        assertThatThrownBy(() -> service.create(request("财务", "finance")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已存在（含已删除角色）");
    }

    @Test
    void builtinRolesCannotBeDeleted() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, "maintainer", "1"));

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不允许删除");
    }

    @Test
    void roleWithAssignedAccountsCannotBeDeleted() {
        when(roleMapper.selectById(6L)).thenReturn(role(6L, "finance", "0"));
        when(userRoleMapper.countUsersByRoleId(6L)).thenReturn(2);

        assertThatThrownBy(() -> service.delete(6L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("还有 2 个账号");
    }

    @Test
    void superAdminRoleCannotBeDisabledOrRegranted() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "admin", "1"));

        assertThatThrownBy(() -> service.changeStatus(1L, "1"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不允许停用");
        assertThatThrownBy(() -> service.grantMenus(1L, List.of(5L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权限不允许");
    }

    @Test
    void menuScopeTrimsNodesOutsideThePackageFromTheGrantTree() {
        SysRoleAdminService scoped = serviceScopedTo(Set.of(5L, 510L, 511L));

        assertThat(ids(scoped.grantableMenuTree())).containsExactlyInAnyOrder(5L, 510L, 511L);
    }

    @Test
    void menuScopeRejectsGrantingAMenuOutsideThePackage() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "operator", "0"));

        assertThatThrownBy(() -> serviceScopedTo(Set.of(5L, 510L)).grantMenus(2L, List.of(540L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不在当前租户的套餐内");
        verify(roleMenuMapper, never()).deleteByRoleId(any());
    }

    private SysRoleAdminService serviceScopedTo(Set<Long> allowedMenuIds) {
        return new SysRoleAdminService(roleMapper, menuMapper, roleMenuMapper, userRoleMapper, tokenService,
                tenantId -> allowedMenuIds);
    }

    private static List<Long> ids(List<GrantMenuVo> tree) {
        List<Long> ids = new ArrayList<>();
        collect(tree, ids);
        return ids;
    }

    private static void collect(List<GrantMenuVo> nodes, List<Long> ids) {
        for (GrantMenuVo node : nodes) {
            ids.add(node.getId());
            collect(node.getChildren(), ids);
        }
    }

    private static SysMenu menu(Long menuId, Long parentId, String name, String type) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        return menu;
    }

    private static SysRole role(Long roleId, String roleKey, String builtin) {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        role.setRoleName(roleKey);
        role.setStatus("0");
        role.setBuiltin(builtin);
        return role;
    }

    private static RoleRequest request(String roleName, String roleKey) {
        RoleRequest request = new RoleRequest();
        request.setRoleName(roleName);
        request.setRoleKey(roleKey);
        request.setRoleSort(5);
        return request;
    }
}
