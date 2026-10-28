package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.domain.SysRole;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMenuMapper;
import com.balancloud.system.mapper.SysRoleMapper;
import com.balancloud.system.mapper.SysUserRoleMapper;
import com.balancloud.system.vo.RoleMenuTreeVo;
import com.balancloud.system.vo.RoleVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysRoleQueryService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    public SysRoleQueryService(SysRoleMapper roleMapper,
                               SysMenuMapper menuMapper,
                               SysRoleMenuMapper roleMenuMapper,
                               SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<RoleVo> list() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysRole::getRoleSort).orderByAsc(SysRole::getRoleId);
        List<RoleVo> vos = new ArrayList<>();
        for (SysRole role : roleMapper.selectList(wrapper)) {
            RoleVo vo = new RoleVo();
            BeanUtils.copyProperties(role, vo);
            vo.setUserCount(userRoleMapper.countUsersByRoleId(role.getRoleId()));
            vo.setMenuCount(roleMenuMapper.selectMenuIdsByRoleId(role.getRoleId()).size());
            vos.add(vo);
        }
        return vos;
    }

    public RoleMenuTreeVo menuTree(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException(404, "角色不存在");
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMenu::getParentId).orderByAsc(SysMenu::getOrderNum).orderByAsc(SysMenu::getMenuId);
        List<SysMenu> all = menuMapper.selectList(wrapper);

        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : all) {
            menu.setChildren(new ArrayList<>());
        }
        for (SysMenu menu : all) {
            SysMenu parent = menu.getParentId() == null || menu.getParentId() == 0L
                    ? null
                    : all.stream().filter(item -> item.getMenuId().equals(menu.getParentId())).findFirst().orElse(null);
            if (parent == null) {
                roots.add(menu);
            } else {
                parent.getChildren().add(menu);
            }
        }

        RoleMenuTreeVo vo = new RoleMenuTreeVo();
        vo.setRoleId(roleId);
        vo.setMenus(roots);
        vo.setCheckedMenuIds(roleMenuMapper.selectMenuIdsByRoleId(roleId));
        return vo;
    }
}
