package com.balancloud.system.service;

import com.balancloud.system.domain.SysMenu;
import com.balancloud.system.mapper.SysMenuMapper;
import com.balancloud.system.vo.RouterVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysMenuServiceRoutingTest {

    private final SysMenuMapper mapper = mock(SysMenuMapper.class);
    private final SysMenuService service = new SysMenuService(mapper);

    @Test
    void topLevelPageMenuIsWrappedInLayout() {
        when(mapper.selectRouterMenus()).thenReturn(List.of(
                menu(10L, 0L, "message", "balan/system/message/index")));

        RouterVo router = service.getRouters(1L, true).get(0);

        assertThat(router.getPath()).isEqualTo("/message");
        assertThat(router.getComponent()).isEqualTo("Layout");
        assertThat(router.getChildren()).hasSize(1);
        assertThat(router.getChildren().get(0).getPath()).isEqualTo("message");
        assertThat(router.getChildren().get(0).getComponent()).isEqualTo("balan/system/message/index");
    }

    @Test
    void orphanPageMenuIsReparentedToRoot() {
        // 角色只授权了子菜单却没授权父目录：父节点缺失时按顶级菜单下发，避免前端收到无前导斜杠的路径
        when(mapper.selectRouterMenusByUserId(9L)).thenReturn(List.of(
                menu(570L, 5L, "message", "balan/system/message/index")));

        RouterVo router = service.getRouters(9L, false).get(0);

        assertThat(router.getPath()).isEqualTo("/message");
        assertThat(router.getComponent()).isEqualTo("Layout");
        assertThat(router.getChildren()).hasSize(1);
    }

    @Test
    void directoryWithChildrenKeepsNestedPages() {
        SysMenu directory = menu(5L, 0L, "message", null);
        directory.setMenuType("M");
        when(mapper.selectRouterMenus()).thenReturn(List.of(
                directory, menu(570L, 5L, "message", "balan/system/message/index")));

        RouterVo router = service.getRouters(1L, true).get(0);

        assertThat(router.getPath()).isEqualTo("/message");
        assertThat(router.getComponent()).isEqualTo("Layout");
        assertThat(router.getAlwaysShow()).isTrue();
        assertThat(router.getChildren()).hasSize(1);
        assertThat(router.getChildren().get(0).getPath()).isEqualTo("message");
    }

    private SysMenu menu(Long id, Long parentId, String path, String component) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuName(path);
        menu.setMenuType("C");
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIsFrame("1");
        menu.setVisible("0");
        menu.setIsCache("1");
        menu.setOrderNum(1);
        return menu;
    }
}
