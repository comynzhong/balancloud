-- 演示账号：admin/admin123、operator/operator123、maintainer/maintainer123、viewer/viewer123
INSERT INTO sys_user (user_id, user_name, nick_name, password, email, status)
VALUES (1, 'admin', '超级管理员', '$2b$10$YKvsiO.otNpr2ltFGg0vUuMp7kptD1iWTFJVq8kPNphfo098MLUnK', 'admin@balancloud.local', '0'),
       (2, 'operator', '场站运营', '$2b$10$y/wOA/IH.kHUPohHZ2yC0.Ih32N425TRR3blP.Pq5Z2dlaPIS79AS', 'operator@balancloud.local', '0'),
       (3, 'maintainer', '运维人员', '$2b$10$Bx4.dWHYcd.MEGPNJAQekuOJqb4J4yPBq9Mk/ucCHE5ASWLxL.RAi', 'maintainer@balancloud.local', '0'),
       (4, 'viewer', '访客', '$2b$10$cCl6byh3rR9s8nyVWwCzU.UsqYJziCkPulYPPMRVONldaAZHlNZ9a', 'viewer@balancloud.local', '0');

INSERT INTO sys_role (role_id, role_name, role_key, role_sort, status, remark)
VALUES (1, '超级管理员', 'admin', 1, '0', '系统内置角色，拥有全部权限'),
       (2, '场站运营', 'operator', 2, '0', '管理授权场站的费率、订单与设备启停'),
       (3, '运维人员', 'maintainer', 3, '0', '查看设备并处理授权场站告警'),
       (4, '访客', 'viewer', 4, '0', '只读查看大盘、设备、订单与告警');

INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1), (2, 2), (3, 3), (4, 4);

-- 演示场站数据权限占位；station 业务表在 M1 建立
INSERT INTO sys_user_station (user_id, station_id)
VALUES (2, 1), (3, 1), (4, 1);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
VALUES (1, '运营管理', 0, 1, 'operations', NULL, 'M', '0', '0', NULL, 'tree-table'),
       (100, '场站管理', 1, 1, 'station', 'balan/station/index', 'C', '0', '0', 'station:list', 'guide'),
       (101, '场站查询', 100, 1, '', NULL, 'F', '0', '0', 'station:query', '#'),
       (102, '场站新增', 100, 2, '', NULL, 'F', '0', '0', 'station:add', '#'),
       (103, '场站修改', 100, 3, '', NULL, 'F', '0', '0', 'station:edit', '#'),
       (104, '场站删除', 100, 4, '', NULL, 'F', '0', '0', 'station:remove', '#'),
       (110, '充电桩管理', 1, 2, 'charger', 'balan/charger/index', 'C', '0', '0', 'charger:list', 'server'),
       (111, '充电桩查询', 110, 1, '', NULL, 'F', '0', '0', 'charger:query', '#'),
       (112, '充电桩新增', 110, 2, '', NULL, 'F', '0', '0', 'charger:add', '#'),
       (113, '充电桩修改', 110, 3, '', NULL, 'F', '0', '0', 'charger:edit', '#'),
       (114, '充电桩删除', 110, 4, '', NULL, 'F', '0', '0', 'charger:remove', '#'),
       (115, '启动充电', 110, 5, '', NULL, 'F', '0', '0', 'charger:start', '#'),
       (116, '停止充电', 110, 6, '', NULL, 'F', '0', '0', 'charger:stop', '#'),
       (117, '远程解锁', 110, 7, '', NULL, 'F', '0', '0', 'charger:unlock', '#'),
       (118, '参数下发', 110, 8, '', NULL, 'F', '0', '0', 'charger:threshold', '#'),
       (120, '费率策略', 1, 3, 'rate', 'balan/rate/index', 'C', '0', '0', 'rate:list', 'money'),
       (121, '费率查询', 120, 1, '', NULL, 'F', '0', '0', 'rate:query', '#'),
       (122, '费率新增', 120, 2, '', NULL, 'F', '0', '0', 'rate:add', '#'),
       (123, '费率修改', 120, 3, '', NULL, 'F', '0', '0', 'rate:edit', '#'),
       (124, '费率删除', 120, 4, '', NULL, 'F', '0', '0', 'rate:remove', '#'),
       (125, '费率启用', 120, 5, '', NULL, 'F', '0', '0', 'rate:enable', '#'),
       (2, '订单管理', 0, 2, 'order', NULL, 'M', '0', '0', NULL, 'list'),
       (200, '充电订单', 2, 1, 'list', 'balan/order/index', 'C', '0', '0', 'order:list', 'list'),
       (201, '订单查询', 200, 1, '', NULL, 'F', '0', '0', 'order:query', '#'),
       (202, '模拟支付', 200, 2, '', NULL, 'F', '0', '0', 'order:pay', '#'),
       (203, '订单导出', 200, 3, '', NULL, 'F', '0', '0', 'order:export', '#'),
       (3, '告警管理', 0, 3, 'alert', NULL, 'M', '0', '0', NULL, 'message'),
       (300, '告警记录', 3, 1, 'record', 'balan/alert/index', 'C', '0', '0', 'alert:list', 'message'),
       (301, '确认告警', 300, 1, '', NULL, 'F', '0', '0', 'alert:ack', '#'),
       (302, '恢复告警', 300, 2, '', NULL, 'F', '0', '0', 'alert:resolve', '#'),
       (4, '仿真器', 0, 4, 'simulator', NULL, 'M', '0', '0', NULL, 'tool'),
       (400, '仿真控制', 4, 1, 'control', 'balan/simulator/index', 'C', '0', '0', 'simulator:list', 'tool'),
       (401, '启动仿真', 400, 1, '', NULL, 'F', '0', '0', 'simulator:start', '#'),
       (402, '停止仿真', 400, 2, '', NULL, 'F', '0', '0', 'simulator:stop', '#'),
       (403, '故障注入', 400, 3, '', NULL, 'F', '0', '0', 'simulator:fault', '#'),
       (404, '修改仿真配置', 400, 4, '', NULL, 'F', '0', '0', 'simulator:config', '#'),
       (5, '系统管理', 0, 5, 'system', NULL, 'M', '0', '0', NULL, 'system'),
       (500, '用户管理', 5, 1, 'user', 'balan/system/user/index', 'C', '0', '0', 'system:user:list', 'user'),
       (501, '用户查询', 500, 1, '', NULL, 'F', '0', '0', 'system:user:query', '#'),
       (510, '角色管理', 5, 2, 'role', 'balan/system/role/index', 'C', '0', '0', 'system:role:list', 'peoples'),
       (511, '角色查询', 510, 1, '', NULL, 'F', '0', '0', 'system:role:query', '#'),
       (520, '审计日志', 5, 3, 'log', 'balan/system/log/index', 'C', '0', '0', 'system:log:list', 'log'),
       (521, '日志查询', 520, 1, '', NULL, 'F', '0', '0', 'system:log:query', '#');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (2, 1), (2, 100), (2, 101), (2, 110), (2, 111), (2, 115), (2, 116), (2, 117), (2, 118),
       (2, 120), (2, 121), (2, 122), (2, 123), (2, 124), (2, 125),
       (2, 2), (2, 200), (2, 201), (2, 202), (2, 203),
       (2, 3), (2, 300);

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (3, 1), (3, 100), (3, 101), (3, 110), (3, 111),
       (3, 3), (3, 300), (3, 301), (3, 302);

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (4, 1), (4, 100), (4, 101), (4, 110), (4, 111),
       (4, 120), (4, 121),
       (4, 2), (4, 200), (4, 201),
       (4, 3), (4, 300);
