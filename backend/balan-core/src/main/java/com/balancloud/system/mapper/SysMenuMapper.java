package com.balancloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.balancloud.system.domain.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("""
            SELECT menu_id, menu_name, parent_id, order_num, path, component, query,
                   is_frame, is_cache, menu_type, visible, status, perms, icon, create_time
            FROM sys_menu
            WHERE menu_type IN ('M', 'C') AND status = '0'
            ORDER BY parent_id, order_num, menu_id
            """)
    List<SysMenu> selectRouterMenus();

    /**
     * 角色停用或删除后必须连带收回菜单：sys_role_menu 里有记录不等于角色还有效。
     * 这里不加 status/del_flag 条件的话，会出现 roles 为空但侧边栏和按钮照旧。
     */
    @Select("""
            SELECT DISTINCT m.menu_id, m.menu_name, m.parent_id, m.order_num, m.path, m.component,
                   m.query, m.is_frame, m.is_cache, m.menu_type, m.visible, m.status, m.perms,
                   m.icon, m.create_time
            FROM sys_menu m
            JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            JOIN sys_user_role ur ON rm.role_id = ur.role_id
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND m.menu_type IN ('M', 'C') AND m.status = '0'
              AND r.status = '0' AND r.del_flag = '0'
            ORDER BY m.parent_id, m.order_num, m.menu_id
            """)
    List<SysMenu> selectRouterMenusByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT m.perms
            FROM sys_menu m
            JOIN sys_role_menu rm ON m.menu_id = rm.menu_id
            JOIN sys_user_role ur ON rm.role_id = ur.role_id
            JOIN sys_role r ON r.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND m.status = '0'
              AND r.status = '0' AND r.del_flag = '0'
              AND m.perms IS NOT NULL AND m.perms <> ''
            """)
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
