-- 批次 A8-A10：设备分析、黑名单轻量版、占位费（§30 拍板后的 core 追加项）。

-- 黑名单：用户维度与车牌维度共用一张表，scope_type 区分；解除用 status 标记，保留审计痕迹。
CREATE TABLE IF NOT EXISTS app_user_blacklist (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '黑名单ID',
    tenant_id     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    scope_type    VARCHAR(10)  NOT NULL COMMENT '维度：USER 用户 / PLATE 车牌',
    app_user_id   BIGINT       DEFAULT NULL COMMENT '小程序用户ID（USER 维度必填）',
    plate_no      VARCHAR(20)  DEFAULT NULL COMMENT '车牌号（PLATE 维度必填）',
    reason        VARCHAR(200) NOT NULL COMMENT '拉黑原因',
    status        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0生效 1已解除',
    expire_at     DATETIME     DEFAULT NULL COMMENT '失效时间，NULL 表示永久',
    create_by     VARCHAR(64)  DEFAULT '',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64)  DEFAULT '',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_blacklist_user (app_user_id, status),
    KEY idx_blacklist_plate (plate_no, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小程序用户/车牌黑名单';

-- 占位费：设备上报插枪状态，订单结束后仍插枪进入 OCCUPIED，按配置累加占用费。
ALTER TABLE charger_port
    ADD COLUMN plugged CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否插枪 1是 0否' AFTER last_metric_at,
    ADD COLUMN occupied_since DATETIME DEFAULT NULL COMMENT '进入占用的时间' AFTER plugged,
    ADD COLUMN occupancy_order_no VARCHAR(32) DEFAULT NULL COMMENT '占用费归属订单号' AFTER occupied_since;

ALTER TABLE charge_order
    ADD COLUMN occupancy_seconds INT NOT NULL DEFAULT 0 COMMENT '占位时长（秒）' AFTER service_fee,
    ADD COLUMN occupancy_fee DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '占位费（元）' AFTER occupancy_seconds;

INSERT IGNORE INTO sys_config
(config_key, config_name, config_value, value_type, config_group, sort, options_json,
 min_value, max_value, max_length, remark)
VALUES
('operations.occupancy.enabled', '占位费开关', 'false', 'BOOLEAN', 'operations', 11, NULL, NULL, NULL, NULL,
 '开启后订单结束仍插枪按占用时长计费；拔枪或重新启动时结束占用'),
('operations.occupancy.pricePerHour', '占位费单价（元/小时）', '2.00', 'DECIMAL', 'operations', 12, NULL, 0, 1000, NULL,
 '从免费宽限期结束后开始计费'),
('operations.occupancy.graceMinutes', '占位免费宽限（分钟）', '15', 'INT', 'operations', 13, NULL, 0, 1440, NULL,
 '订单结束后的免费搬车时间'),
('operations.occupancy.maxFee', '占位费封顶（元）', '20.00', 'DECIMAL', 'operations', 14, NULL, 0, 10000, NULL,
 '0 表示不封顶');

-- 运营管理 → 黑名单管理
-- 数据中心 → 设备分析；运营管理 → 充电桩管理下新增「拔枪释放」按钮权限
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                             menu_type, visible, status, perms, icon)
VALUES (160, '黑名单管理', 1, 7, 'blacklist', 'balan/blacklist/index', NULL, '1', '0', 'C', '0', '0',
        'blacklist:list', 'lock'),
       (161, '黑名单查询', 160, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'blacklist:query', '#'),
       (162, '黑名单新增', 160, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'blacklist:add', '#'),
       (163, '黑名单修改', 160, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'blacklist:edit', '#'),
       (164, '黑名单删除', 160, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'blacklist:remove', '#'),
       (6, '数据中心', 0, 6, 'analytics', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'chart'),
       (600, '设备分析', 6, 1, 'device', 'balan/analytics/device', NULL, '1', '0', 'C', '0', '0',
        'analytics:device:list', 'chart'),
       (601, '分析查询', 600, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'analytics:device:query', '#'),
       (119, '拔枪释放', 110, 9, '', NULL, NULL, '1', '0', 'F', '0', '0', 'charger:unplug', '#');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (6, 119, 160, 161, 162, 163, 164, 600, 601);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (2, 6), (2, 600), (2, 601), (2, 160), (2, 161), (2, 162), (2, 163), (2, 164), (2, 119),
       (3, 6), (3, 600), (3, 601), (3, 160), (3, 161),
       (4, 6), (4, 600), (4, 601);
