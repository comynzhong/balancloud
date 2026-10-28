package com.balancloud.system.service;

import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.vo.MetaVo;
import com.balancloud.system.vo.RouterVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysMenuService {

    private final SysMenuMapper menuMapper;

    public SysMenuService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    public List<RouterVo> getRouters(Long userId, boolean admin) {
        List<SysMenu> menus = admin ? menuMapper.selectRouterMenus() : menuMapper.selectRouterMenusByUserId(userId);
        return buildRouters(buildTree(menus));
    }

    private List<SysMenu> buildTree(List<SysMenu> menus) {
        Map<Long, SysMenu> menuMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            menu.setChildren(new ArrayList<>());
            menuMap.put(menu.getMenuId(), menu);
        }
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            SysMenu parent = menuMap.get(menu.getParentId());
            if (parent != null) {
                parent.getChildren().add(menu);
            } else {
                // 角色只授权了子菜单而没授权父目录时，按顶级菜单下发，避免前端收到无前导斜杠的路径
                if (menu.getParentId() != null && menu.getParentId() != 0L) {
                    menu.setParentId(0L);
                }
                roots.add(menu);
            }
        }
        sortMenus(roots);
        return roots;
    }

    private void sortMenus(List<SysMenu> menus) {
        menus.sort(Comparator.comparing(SysMenu::getOrderNum, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysMenu::getMenuId));
        menus.forEach(menu -> sortMenus(menu.getChildren()));
    }

    private List<RouterVo> buildRouters(List<SysMenu> menus) {
        List<RouterVo> routers = new ArrayList<>();
        for (SysMenu menu : menus) {
            RouterVo router = new RouterVo();
            router.setHidden("1".equals(menu.getVisible()));
            router.setName(routeName(menu));
            router.setPath(routerPath(menu));
            router.setComponent(component(menu));
            router.setQuery(menu.getQuery());
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), "1".equals(menu.getIsCache()), null));

            List<SysMenu> children = menu.getChildren();
            if (!children.isEmpty() && "M".equals(menu.getMenuType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildRouters(children));
            } else if (isTopLevelMenu(menu)) {
                // 顶级 C 菜单需要外层 Layout 承载，页面组件挂到唯一子路由上
                router.setComponent("Layout");
                router.setMeta(null);
                RouterVo child = new RouterVo();
                child.setPath(menu.getPath());
                child.setComponent(menu.getComponent());
                child.setName(capitalize(menu.getPath()));
                child.setQuery(menu.getQuery());
                child.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), "1".equals(menu.getIsCache()), null));
                router.setChildren(List.of(child));
            }
            routers.add(router);
        }
        return routers;
    }

    private boolean isTopLevelMenu(SysMenu menu) {
        return menu.getParentId() != null && menu.getParentId() == 0L
                && "C".equals(menu.getMenuType())
                && "1".equals(menu.getIsFrame());
    }

    private String routeName(SysMenu menu) {
        if (isTopLevelMenu(menu)) {
            return "";
        }
        return capitalize(menu.getPath());
    }

    private String routerPath(SysMenu menu) {
        String path = menu.getPath() == null ? "" : menu.getPath();
        if ((menu.getParentId() != null && menu.getParentId() == 0L)
                && ("M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))) {
            return "/" + path;
        }
        return path;
    }

    private String component(SysMenu menu) {
        String component = menu.getComponent();
        if (component != null && !component.isBlank() && !"Layout".equals(component)) {
            return component;
        }
        if (menu.getParentId() != null && menu.getParentId() != 0L && "M".equals(menu.getMenuType())) {
            return "ParentView";
        }
        return "Layout";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("/", "");
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
