package com.balancloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.balancloud.common.exception.ServiceException;
import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.dto.MenuQuery;
import com.balancloud.system.dto.MenuRequest;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.mapper.SysRoleMenuMapper;
import com.balancloud.system.vo.MenuTreeVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理：sys_menu 的查询与维护。路由下发由 {@link SysMenuService} 负责，
 * 因此这里必须挡住会破坏路由树的结构（例如挂在菜单下的目录）。
 */
@Service
public class SysMenuAdminService {

    private static final String TYPE_DIR = "M";
    private static final String TYPE_MENU = "C";
    private static final String TYPE_BUTTON = "F";
    private static final List<String> TYPES = List.of(TYPE_DIR, TYPE_MENU, TYPE_BUTTON);

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public SysMenuAdminService(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    public List<SysMenu> list(MenuQuery query) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .like(StringUtils.hasText(query.getMenuName()), SysMenu::getMenuName, normalize(query.getMenuName()))
                .eq(StringUtils.hasText(query.getStatus()), SysMenu::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getVisible()), SysMenu::getVisible, query.getVisible())
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum)
                .orderByAsc(SysMenu::getMenuId);
        return menuMapper.selectList(wrapper);
    }

    public SysMenu detail(Long menuId) {
        return requireMenu(menuId);
    }

    /**
     * 上级菜单下拉树。编辑时排除自身及其子孙，否则会把菜单挂进自己的子树里。
     */
    public List<MenuTreeVo> options(Long excludeId) {
        List<SysMenu> menus = listAll();
        Set<Long> blocked = excludeId == null ? Set.of() : selfAndDescendants(menus, excludeId);
        List<SysMenu> candidates = menus.stream()
                .filter(menu -> !blocked.contains(menu.getMenuId()))
                .filter(menu -> !TYPE_BUTTON.equals(menu.getMenuType()))
                .toList();
        return toTree(candidates);
    }

    @Transactional
    public Long create(MenuRequest request) {
        SysMenu entity = new SysMenu();
        apply(entity, request, null);
        menuMapper.insert(entity);
        return entity.getMenuId();
    }

    @Transactional
    public void update(Long menuId, MenuRequest request) {
        SysMenu existing = requireMenu(menuId);
        apply(existing, request, menuId);
        menuMapper.updateById(existing);
    }

    @Transactional
    public void delete(Long menuId) {
        requireMenu(menuId);
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId)) > 0) {
            throw new ServiceException("存在子菜单，请先删除子菜单");
        }
        if (roleMenuMapper.countByMenuId(menuId) > 0) {
            throw new ServiceException("该菜单已分配给角色，请先在角色授权中取消");
        }
        menuMapper.deleteById(menuId);
    }

    private void apply(SysMenu entity, MenuRequest request, Long selfId) {
        String type = request.getMenuType() == null ? "" : request.getMenuType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new ServiceException("菜单类型只能是 M（目录）/ C（菜单）/ F（按钮）");
        }
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        String menuName = requireText(request.getMenuName(), "菜单名称不能为空");
        String path = normalize(request.getPath());
        String component = normalize(request.getComponent());
        String perms = normalize(request.getPerms());

        if (parentId != 0L) {
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new ServiceException("上级菜单不存在");
            }
            if (selfId != null && selfAndDescendants(listAll(), selfId).contains(parentId)) {
                throw new ServiceException("上级菜单不能是自己或自己的下级");
            }
            if (TYPE_BUTTON.equals(parent.getMenuType())) {
                throw new ServiceException("按钮下不能再挂菜单");
            }
            if (TYPE_MENU.equals(parent.getMenuType()) && !TYPE_BUTTON.equals(type)) {
                throw new ServiceException("菜单下只能挂按钮，目录或子菜单请挂到目录上");
            }
            if (TYPE_DIR.equals(type) && !TYPE_DIR.equals(parent.getMenuType())) {
                throw new ServiceException("目录只能挂在目录或顶级位置下");
            }
        } else if (!TYPE_DIR.equals(type) && !TYPE_MENU.equals(type)) {
            throw new ServiceException("按钮必须挂在某个菜单下");
        }

        if ((TYPE_DIR.equals(type) || TYPE_MENU.equals(type)) && path.isEmpty()) {
            throw new ServiceException("路由地址不能为空");
        }
        if (TYPE_MENU.equals(type) && component.isEmpty() && !path.startsWith("http")) {
            throw new ServiceException("菜单必须填写组件路径，或把路由地址填为外链");
        }
        if (TYPE_BUTTON.equals(type) && perms.isEmpty()) {
            throw new ServiceException("按钮必须填写权限标识");
        }
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuName, menuName)
                .eq(SysMenu::getParentId, parentId)
                .ne(selfId != null, SysMenu::getMenuId, selfId)) > 0) {
            throw new ServiceException("同级下已存在「" + menuName + "」");
        }

        entity.setMenuName(menuName);
        entity.setParentId(parentId);
        entity.setOrderNum(request.getOrderNum());
        entity.setPath(path);
        entity.setComponent(component);
        entity.setQuery(normalize(request.getQuery()));
        entity.setIsFrame(defaultFlag(request.getIsFrame(), "1"));
        entity.setIsCache(defaultFlag(request.getIsCache(), "0"));
        entity.setMenuType(type);
        entity.setVisible(defaultFlag(request.getVisible(), "0"));
        entity.setStatus(defaultFlag(request.getStatus(), "0"));
        entity.setPerms(perms);
        entity.setIcon(normalize(request.getIcon()));
    }

    private List<SysMenu> listAll() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum)
                .orderByAsc(SysMenu::getMenuId));
    }

    private Set<Long> selfAndDescendants(List<SysMenu> menus, Long rootId) {
        Map<Long, List<Long>> childIds = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            childIds.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu.getMenuId());
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            childIds.getOrDefault(current, List.of()).forEach(stack::push);
        }
        return visited;
    }

    private List<MenuTreeVo> toTree(List<SysMenu> menus) {
        Map<Long, MenuTreeVo> nodes = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            MenuTreeVo node = new MenuTreeVo();
            node.setId(menu.getMenuId());
            node.setLabel(menu.getMenuName());
            nodes.put(menu.getMenuId(), node);
        }
        List<MenuTreeVo> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            MenuTreeVo node = nodes.get(menu.getMenuId());
            MenuTreeVo parent = nodes.get(menu.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private SysMenu requireMenu(Long menuId) {
        SysMenu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new ServiceException(404, "菜单不存在");
        }
        return menu;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultFlag(String value, String fallback) {
        return "0".equals(value) || "1".equals(value) ? value : fallback;
    }
}
