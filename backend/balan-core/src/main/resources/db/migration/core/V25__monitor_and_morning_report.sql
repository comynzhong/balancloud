-- 系统监控（节点状态）与租户运营晨报：菜单 700 段与 170 段，均属社区版能力。
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (700, '系统监控', 0, 7, 'monitor', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'monitor'),
       (701, '节点状态', 700, 1, 'node', 'balan/monitor/node/index', NULL, '1', '0', 'C', '0', '0',
        'monitor:node:list', 'server'),
       (702, '状态查询', 701, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'monitor:node:query', '#'),
       (170, '运营晨报', 1, 8, 'report', 'balan/report/morning/index', NULL, '1', '0', 'C', '0', '0',
        'report:morning:list', 'documentation'),
       (171, '推送晨报', 170, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'report:morning:push', '#');

-- 中间件连通性属于运维面，只给超管与运维；晨报是经营数据，只读角色也能看。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 700 FROM sys_role r WHERE r.role_key IN ('admin', 'maintainer');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 701 FROM sys_role r WHERE r.role_key IN ('admin', 'maintainer');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 702 FROM sys_role r WHERE r.role_key IN ('admin', 'maintainer');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 170 FROM sys_role r WHERE r.role_key IN ('admin', 'operator', 'viewer');
-- 手动推送是租户级动作（与每日 08:00 的站内信口径一致），只给超管。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 171 FROM sys_role r WHERE r.role_key = 'admin';
