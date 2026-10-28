-- M3.5 系统管理界面：补齐用户/角色/审计按钮权限（菜单 id 5xx 系列已在 V2 建立）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon)
VALUES (502, '用户新增', 500, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:user:add', '#'),
       (503, '用户修改', 500, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:user:edit', '#'),
       (504, '用户删除', 500, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:user:remove', '#'),
       (505, '重置密码', 500, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:user:resetPwd', '#'),
       (522, '日志导出', 520, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'system:log:export', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (5, 500, 501, 502, 503, 504, 505, 510, 511, 520, 521, 522);
