-- A1 角色管理与菜单授权可视化：内置角色标记 + 角色写操作的按钮权限

ALTER TABLE sys_role
    ADD COLUMN builtin CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否内置角色：1=种子角色，禁止删除、role_key 只读' AFTER role_key;

UPDATE sys_role SET builtin = '1' WHERE role_id IN (1, 2, 3, 4);

-- 角色管理（510）下补四个写操作按钮；511 角色查询已由 V7 建立，540-544 已被 V12 菜单管理占用
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (550, '角色新增', 510, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:role:add', '#'),
       (551, '角色修改', 510, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:role:edit', '#'),
       (552, '角色删除', 510, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:role:remove', '#'),
       (553, '角色授权', 510, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:role:grant', '#');

-- 只授权给超级管理员角色；其他角色需要时由管理员在角色授权界面勾选
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (550, 551, 552, 553);
