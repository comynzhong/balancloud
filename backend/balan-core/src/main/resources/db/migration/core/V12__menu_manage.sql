-- 菜单管理：让运营可以在后台查看与维护 sys_menu 树（路由由 /getRouters 实时读取，改完刷新即生效）
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (540, '菜单管理', 5, 5, 'menu', 'balan/system/menu/index', NULL, '1', '0', 'C', '0', '0', 'system:menu:list', 'tree-table'),
       (541, '菜单查询', 540, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:menu:query', '#'),
       (542, '菜单新增', 540, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:menu:add', '#'),
       (543, '菜单修改', 540, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:menu:edit', '#'),
       (544, '菜单删除', 540, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:menu:remove', '#');

-- 仅授权给超级管理员角色；operator/viewer 需要菜单管理时由管理员在角色授权里勾选
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (540, 541, 542, 543, 544);
